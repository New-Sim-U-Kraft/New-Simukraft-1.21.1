package common.cn.kafei.simukraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SimuKraftCommandTest {
    @Test
    void registersNpcDiseaseSetAndClearCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SimuKraftCommand.register(dispatcher);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("simukraft");
        CommandNode<CommandSourceStack> disease = root.getChild("npc").getChild("disease");
        CommandNode<CommandSourceStack> setCitizen = disease.getChild("set").getChild("citizen");

        assertNotNull(setCitizen.getChild("disease"));
        assertNotNull(disease.getChild("clear").getChild("citizen"));
    }

    @Test
    void registersNpcHungerSetCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SimuKraftCommand.register(dispatcher);

        CommandNode<CommandSourceStack> hunger = dispatcher.getRoot().getChild("simukraft")
                .getChild("npc").getChild("hunger");

        assertNotNull(hunger.getChild("set").getChild("citizen").getChild("hunger"));
    }

    @Test
    void registersNpcPregnancyCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SimuKraftCommand.register(dispatcher);

        CommandNode<CommandSourceStack> pregnancy = dispatcher.getRoot().getChild("simukraft")
                .getChild("npc").getChild("pregnancy");

        assertNotNull(pregnancy.getChild("start").getChild("citizen"));
        CommandNode<CommandSourceStack> progress = pregnancy.getChild("progress");
        assertNotNull(progress.getChild("add").getChild("citizen").getChild("days"));
        assertNotNull(progress.getChild("remove").getChild("citizen").getChild("days"));
    }
}
