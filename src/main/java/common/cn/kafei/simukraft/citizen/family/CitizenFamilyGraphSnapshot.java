package common.cn.kafei.simukraft.citizen.family;

import java.util.List;
import java.util.UUID;

/** 五代直系关系图快照：祖父母、父母、自己、子女、孙子女。 */
public record CitizenFamilyGraphSnapshot(UUID focusId, String focusName, List<Node> nodes, List<Link> links) {
    public CitizenFamilyGraphSnapshot {
        focusId = focusId != null ? focusId : new UUID(0L, 0L);
        focusName = focusName != null ? focusName : "";
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        links = links == null ? List.of() : List.copyOf(links);
    }

    public static CitizenFamilyGraphSnapshot empty() {
        return new CitizenFamilyGraphSnapshot(new UUID(0L, 0L), "", List.of(), List.of());
    }

    /** Node：图上一个市民。generation 相对自己，-2 祖父母到 +2 孙子女。 */
    public record Node(UUID citizenId, String name, String gender, int age, boolean dead, boolean focus,
                       String skinPath, String jobKey, String relationKey, int generation, UUID spouseId) {
        public Node {
            citizenId = citizenId != null ? citizenId : new UUID(0L, 0L);
            name = name != null ? name : "";
            gender = gender != null ? gender : "male";
            skinPath = skinPath != null ? skinPath : "";
            jobKey = jobKey != null ? jobKey : "";
            relationKey = relationKey != null ? relationKey : "relative";
        }
    }

    /** Link：亲子连线，parent 指向 child。 */
    public record Link(UUID parentId, UUID childId) {
        public Link {
            parentId = parentId != null ? parentId : new UUID(0L, 0L);
            childId = childId != null ? childId : new UUID(0L, 0L);
        }
    }
}
