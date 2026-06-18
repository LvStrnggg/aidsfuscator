package dev.lvstrng.aidsfuscator.analysis.flow.graph;

import dev.lvstrng.aidsfuscator.analysis.interpreter.SimpleFrame;
import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.tree.impl.JMethod;
import dev.lvstrng.aidsfuscator.utils.ASMUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constructs the control flow graph of a method.
 * @see Block
 * @author lvstrng
 */
public class ControlFlowGraph {
    private final Context context;
    private final JMethod method;

    private final List<Block> blocks;
    private final Map<LabelNode, Block> labelBlocks;
    private final Map<JumpInsnNode, LabelNode> flows;

    private final Map<LabelNode, LabelNode> labels;
    private Map<AbstractInsnNode, SimpleFrame> frames;

    public ControlFlowGraph(Context context, JMethod method) {
        this.context = context;
        this.method = method;

        this.blocks = new ArrayList<>();
        this.labelBlocks = new HashMap<>();
        this.flows = new HashMap<>();
        this.labels = new HashMap<>();
    }

    public ControlFlowGraph build() {
        if(method.insns().size() == 0)
            return this;

        this.prepare();
        this.frames = method.frames(context);
        if(frames == null)
            return this;

        int lineNumber = -1;
        Block currentBlock = null;
        for(var insn : method.insns()) {
            if(insn instanceof LabelNode lbl) {
                var nextBlock = labelBlocks.get(lbl);

                if (currentBlock != null)
                    handleCurrentBlock(currentBlock, lbl, nextBlock);

                currentBlock = nextBlock;
            } else if(insn instanceof LineNumberNode ln)
                lineNumber = ln.line;

            if(currentBlock == null)
                continue;

            var frame = frameAt(insn);
            if(frame != null) {
                if(currentBlock.start() == null)
                    currentBlock.setStart(frame);
                currentBlock.setEnd(frame);
            }

            currentBlock.setLineNumber(lineNumber);
            currentBlock.frames().put(insn, frame);
            currentBlock.setLastInsn(insn);
            currentBlock.insns().add(insn);

            switch (insn) {
                case JumpInsnNode jmp -> {
                    var target = get(jmp.label);
                    currentBlock.vertex(target);

                    if(jmp.getOpcode() == Opcodes.GOTO) {
                        currentBlock.setDefaultBlock(target);
                        currentBlock = null;
                        break;
                    }

                    var fallThru = get(flows.get(jmp));
                    currentBlock.setDefaultBlock(fallThru);
                    currentBlock.vertex(fallThru);
                    currentBlock = null;
                }
                case LookupSwitchInsnNode sw -> {
                    var defaultBlock = get(sw.dflt);
                    currentBlock.setDefaultBlock(defaultBlock);
                    currentBlock.vertex(defaultBlock);

                    for(var route : sw.labels) {
                        currentBlock.vertex(get(route));
                    }

                    currentBlock = null;
                }
                case TableSwitchInsnNode sw -> {
                    var defaultBlock = get(sw.dflt);
                    currentBlock.setDefaultBlock(defaultBlock);
                    currentBlock.vertex(defaultBlock);

                    for(var route : sw.labels) {
                        currentBlock.vertex(get(route));
                    }

                    currentBlock = null;
                }
                default -> {
                    if(ASMUtils.isReturn(insn) || insn.getOpcode() == Opcodes.ATHROW) {
                        currentBlock = null;
                    }
                }
            }
        }

        for(var block : blocks) {
            if(block.start() == null)
                continue;

            if(block.start().getStackSize() == 0)
                continue;

            block.setExpectsValue();
            for(var predecessor : block.predecessors()) {
                predecessor.setCarryingValue();
            }
        }

        return this;
    }

    private void prepare() {
        if(!(method.insns().getFirst() instanceof LabelNode))
            method.insns().insert(new LabelNode());

        for(var insn : method.insns()) {
            if(!(insn instanceof JumpInsnNode jmp))
                continue;

            if(jmp.getOpcode() == Opcodes.GOTO) {
                flows.put(jmp, jmp.label);
                continue;
            }

            if(jmp.getNext() instanceof LabelNode lbl) {
                flows.put(jmp, lbl);
                continue;
            }

            var lbl = new LabelNode();
            method.insns().insert(jmp, lbl);
            flows.put(jmp, lbl);
        }

        int idx = 0;
        for(var insn : method.insns()) {
            if(!(insn instanceof LabelNode lbl))
                continue;

            var block = new Block(lbl, idx++);

            blocks.add(block);
            labelBlocks.put(lbl, block);
            labels.put(lbl, lbl);
        }

        for(var trap : method.traps()) {
            var startBlock = get(trap.start);
            var endBlock = get(trap.end);
            var handlerBlock = get(trap.handler);

            startBlock.traps().add(trap);
            endBlock.trapEnds().add(trap);
            handlerBlock.trapHandlers().add(trap);
        }
    }

    private void handleCurrentBlock(Block currentBlock, LabelNode currentLabel, Block nextBlock) {
        if(currentBlock.defaultBlock() == null) {
            currentBlock.setDefaultBlock(nextBlock);
            currentBlock.vertex(nextBlock);
        }

        nextBlock.traps().addAll(currentBlock.traps());
        nextBlock.trapEnds().addAll(currentBlock.trapEnds());
        nextBlock.trapHandlers().addAll(currentBlock.trapHandlers());

        for(var trap : method.traps()) {
            if(trap.end == currentLabel)
                nextBlock.traps().remove(trap);

            if(trap.handler == currentLabel && trap.end != currentLabel)
                nextBlock.trapEnds().remove(trap);

            if(nextBlock.predecessors().stream().anyMatch(e -> e.trapEnds().contains(trap)))
                nextBlock.trapHandlers().remove(trap);
        }
    }

    public Block firstBlock() {
        return blocks.getFirst();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public List<Block> blocks() {
        return blocks;
    }

    public SimpleFrame frameAt(AbstractInsnNode insn) {
        return frames.get(insn);
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractInsnNode> T clone(T insn) {
        return (T) insn.clone(labels);
    }

    public Block get(LabelNode label) {
        return labelBlocks.get(label);
    }

    public Block blockContaining(AbstractInsnNode insn) {
        return blocks.stream().filter(e -> e.insns().contains(insn)).findFirst().orElseThrow(RuntimeException::new);
    }

    public JMethod method() {
        return method;
    }
}