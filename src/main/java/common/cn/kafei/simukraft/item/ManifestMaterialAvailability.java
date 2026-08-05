package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.material.WorkMaterialPolicy;
import common.cn.kafei.simukraft.material.WorkMaterialRequest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("null")
final class ManifestMaterialAvailability {
    private static final int INFINITE_CAPACITY = Integer.MAX_VALUE / 4;

    private ManifestMaterialAvailability() {
    }

    static Snapshot calculate(List<BuildingBlockData> blocks, int placedEndExclusive, Map<String, Integer> containerItems) {
        Map<String, Integer> required = countMaterials(blocks, 0, blocks.size());
        Map<String, Integer> placed = countMaterials(blocks, 0, placedEndExclusive);
        Map<String, Set<String>> acceptedItems = acceptedItemsByMaterial(blocks);
        Map<String, Integer> allocated = allocateContainerItems(required, placed, acceptedItems, containerItems);
        Map<String, Integer> available = new LinkedHashMap<>();
        required.forEach((itemId, count) -> {
            int found = placed.getOrDefault(itemId, 0) + allocated.getOrDefault(itemId, 0);
            available.put(itemId, Math.min(count, Math.max(0, found)));
        });
        return new Snapshot(required, available);
    }

    private static Map<String, Integer> countMaterials(List<BuildingBlockData> blocks, int startInclusive, int endExclusive) {
        Map<String, Integer> materials = new LinkedHashMap<>();
        int start = clamp(startInclusive, 0, blocks.size());
        int end = clamp(endExclusive, start, blocks.size());
        for (int i = start; i < end; i++) {
            BuildingBlockData block = blocks.get(i);
            if (block == null || block.state() == null || block.state().isAir()) {
                continue;
            }
            if (!WorkMaterialPolicy.requiresMaterial(block.state())) {
                continue;
            }
            String itemId = materialItemId(block.state());
            if (itemId.isEmpty()) {
                continue;
            }
            materials.merge(itemId, 1, Integer::sum);
        }
        return sortByKey(materials);
    }

    private static Map<String, Set<String>> acceptedItemsByMaterial(List<BuildingBlockData> blocks) {
        Map<String, Set<String>> acceptedItems = new LinkedHashMap<>();
        for (BuildingBlockData block : blocks) {
            if (block == null || block.state() == null || block.state().isAir()) {
                continue;
            }
            String materialId = materialItemId(block.state());
            if (materialId.isEmpty()) {
                continue;
            }
            WorkMaterialRequest request = WorkMaterialPolicy.requestForBlock(block.state());
            if (request.isEmpty()) {
                continue;
            }
            Set<String> accepted = acceptedItems.computeIfAbsent(materialId, ignored -> new LinkedHashSet<>());
            accepted.add(materialId);
            for (Item item : request.acceptedItems()) {
                String acceptedId = itemId(item);
                if (!acceptedId.isEmpty()) {
                    accepted.add(acceptedId);
                }
            }
        }
        return acceptedItems;
    }

    private static Map<String, Integer> allocateContainerItems(Map<String, Integer> required,
                                                               Map<String, Integer> placed,
                                                               Map<String, Set<String>> acceptedItems,
                                                               Map<String, Integer> containerItems) {
        Map<String, Integer> remainingNeeds = new LinkedHashMap<>();
        required.forEach((itemId, count) -> {
            int remaining = count - placed.getOrDefault(itemId, 0);
            if (remaining > 0) {
                remainingNeeds.put(itemId, remaining);
            }
        });
        if (remainingNeeds.isEmpty() || containerItems == null || containerItems.isEmpty()) {
            return Map.of();
        }

        List<String> containerItemIds = containerItems.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        List<String> materialIds = new ArrayList<>(remainingNeeds.keySet());
        if (containerItemIds.isEmpty() || materialIds.isEmpty()) {
            return Map.of();
        }

        int source = 0;
        int itemOffset = 1;
        int materialOffset = itemOffset + containerItemIds.size();
        int sink = materialOffset + materialIds.size();
        Dinic dinic = new Dinic(sink + 1);
        for (int itemIndex = 0; itemIndex < containerItemIds.size(); itemIndex++) {
            String containerItemId = containerItemIds.get(itemIndex);
            dinic.addEdge(source, itemOffset + itemIndex, containerItems.getOrDefault(containerItemId, 0));
        }
        for (int itemIndex = 0; itemIndex < containerItemIds.size(); itemIndex++) {
            String containerItemId = containerItemIds.get(itemIndex);
            for (int materialIndex = 0; materialIndex < materialIds.size(); materialIndex++) {
                String materialId = materialIds.get(materialIndex);
                if (accepts(acceptedItems, materialId, containerItemId)) {
                    dinic.addEdge(itemOffset + itemIndex, materialOffset + materialIndex, INFINITE_CAPACITY);
                }
            }
        }
        for (int materialIndex = 0; materialIndex < materialIds.size(); materialIndex++) {
            String materialId = materialIds.get(materialIndex);
            dinic.addEdge(materialOffset + materialIndex, sink, remainingNeeds.getOrDefault(materialId, 0));
        }

        dinic.maxFlow(source, sink);
        Map<String, Integer> allocated = new LinkedHashMap<>();
        for (int itemIndex = 0; itemIndex < containerItemIds.size(); itemIndex++) {
            int itemNode = itemOffset + itemIndex;
            for (Dinic.Edge edge : dinic.edgesFrom(itemNode)) {
                int materialIndex = edge.to() - materialOffset;
                if (materialIndex < 0 || materialIndex >= materialIds.size()) {
                    continue;
                }
                int used = edge.usedCapacity();
                if (used > 0) {
                    allocated.merge(materialIds.get(materialIndex), used, Integer::sum);
                }
            }
        }
        return allocated;
    }

    private static boolean accepts(Map<String, Set<String>> acceptedItems, String materialId, String containerItemId) {
        return acceptedItems.getOrDefault(materialId, Set.of(materialId)).contains(containerItemId);
    }

    private static Map<String, Integer> sortByKey(Map<String, Integer> values) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        entries.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private static String materialItemId(BlockState state) {
        Item item = state.getBlock().asItem();
        return itemId(item);
    }

    private static String itemId(Item item) {
        if (item == null || item == Items.AIR) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record Snapshot(Map<String, Integer> required, Map<String, Integer> available) {
    }

    private static final class Dinic {
        private final List<List<Edge>> graph;
        private final int[] levels;
        private final int[] nextEdgeIndices;

        private Dinic(int nodeCount) {
            graph = new ArrayList<>(nodeCount);
            for (int i = 0; i < nodeCount; i++) {
                graph.add(new ArrayList<>());
            }
            levels = new int[nodeCount];
            nextEdgeIndices = new int[nodeCount];
        }

        private void addEdge(int from, int to, int capacity) {
            if (capacity <= 0) {
                return;
            }
            Edge forward = new Edge(to, graph.get(to).size(), capacity);
            Edge backward = new Edge(from, graph.get(from).size(), 0);
            graph.get(from).add(forward);
            graph.get(to).add(backward);
        }

        private int maxFlow(int source, int sink) {
            int flow = 0;
            while (buildLevels(source, sink)) {
                java.util.Arrays.fill(nextEdgeIndices, 0);
                int pushed;
                while ((pushed = push(source, sink, INFINITE_CAPACITY)) > 0) {
                    flow += pushed;
                }
            }
            return flow;
        }

        private boolean buildLevels(int source, int sink) {
            java.util.Arrays.fill(levels, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            levels[source] = 0;
            queue.add(source);
            while (!queue.isEmpty()) {
                int node = queue.removeFirst();
                for (Edge edge : graph.get(node)) {
                    if (edge.remainingCapacity() <= 0 || levels[edge.to] >= 0) {
                        continue;
                    }
                    levels[edge.to] = levels[node] + 1;
                    queue.add(edge.to);
                }
            }
            return levels[sink] >= 0;
        }

        private int push(int node, int sink, int flow) {
            if (node == sink) {
                return flow;
            }
            List<Edge> edges = graph.get(node);
            while (nextEdgeIndices[node] < edges.size()) {
                Edge edge = edges.get(nextEdgeIndices[node]);
                if (edge.remainingCapacity() > 0 && levels[edge.to] == levels[node] + 1) {
                    int pushed = push(edge.to, sink, Math.min(flow, edge.remainingCapacity()));
                    if (pushed > 0) {
                        edge.capacity -= pushed;
                        graph.get(edge.to).get(edge.reverseIndex).capacity += pushed;
                        return pushed;
                    }
                }
                nextEdgeIndices[node]++;
            }
            return 0;
        }

        private List<Edge> edgesFrom(int node) {
            return graph.get(node);
        }

        private static final class Edge {
            private final int to;
            private final int reverseIndex;
            private final int originalCapacity;
            private int capacity;

            private Edge(int to, int reverseIndex, int capacity) {
                this.to = to;
                this.reverseIndex = reverseIndex;
                this.originalCapacity = capacity;
                this.capacity = capacity;
            }

            private int to() {
                return to;
            }

            private int remainingCapacity() {
                return capacity;
            }

            private int usedCapacity() {
                return originalCapacity - capacity;
            }
        }
    }
}
