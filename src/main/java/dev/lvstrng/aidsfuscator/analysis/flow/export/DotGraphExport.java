package dev.lvstrng.aidsfuscator.analysis.flow.export;

import dev.lvstrng.aidsfuscator.analysis.flow.graph.ControlFlowGraph;
import dev.lvstrng.aidsfuscator.utils.NamedOpcodes;

/**
 * Util that lets you visualize the control flow graph of a method via dot-graph. Currently only used by developers.
 * @author lvstrng
 */
public class DotGraphExport {
    private final ControlFlowGraph cfg;

    public DotGraphExport(ControlFlowGraph cfg) {
        this.cfg = cfg;
    }

    public String export() {
        var sb = new StringBuilder("digraph G {\n");
        sb.append("\tnode [shape=box3d, fontsize = 10];\n");
        sb.append("\tedge [fontsize = 8];\n");

        // add all blocks
        for(var block : cfg.blocks()) {
            sb.append("\t").append(block).append(" [label=\"");
            sb.append(block).append("\\n");

            // display handlers
            if(!block.trapHandlers().isEmpty()) {
                sb.append("In Handler For: \\n");

                for(var trap : block.trapHandlers()) {
                    var start = cfg.get(trap.start);
                    var end = cfg.get(trap.end);

                    sb.append(trap.type).append("=").append("[").append(start).append(" - ").append(end).append("]\\n");
                }
            }

            // header end, display insns
            sb.append("Line ").append(block.lineNumber()).append("\\n");
            sb.append("-------------------------\\n");
            for(var insn : block.insns()) {
                if(insn.getOpcode() == -1)
                    continue;

                sb.append(NamedOpcodes.map(insn.getOpcode())).append("\\n");
            }

            sb.append("\"];\n");
        }

        // add edges/vertices
        for(var block : cfg.blocks()) {
            for(var successor : block.successors()) {
                sb.append("\t").append(block).append(" -> ").append(successor);

                if(block.lastInsn() != null && block.successors().size() != 1) {
                    sb.append(" [label=\"").append(block.lastInsn().getClass().getSimpleName()).append("\"];");
                }

                sb.append("\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }
}
