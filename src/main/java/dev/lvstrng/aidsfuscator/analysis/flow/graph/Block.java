package dev.lvstrng.aidsfuscator.analysis.flow.graph;

import dev.lvstrng.aidsfuscator.analysis.interpreter.SimpleFrame;
import dev.lvstrng.aidsfuscator.analysis.interpreter.SimpleValue;
import dev.lvstrng.aidsfuscator.utils.ASMUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A block in a control flow graph.
 * @see Block
 * @author lvstrng
 */
public class Block {
    private final int idx;
    private final LabelNode label;
    private final List<AbstractInsnNode> insns;
    private final Map<AbstractInsnNode, SimpleFrame> frames;

    private int lineNumber;
    private Block defaultBlock;
    private final List<Block> predecessors, successors;
    private final List<TryCatchBlockNode> traps, trapEnds, trapHandlers;

    private SimpleFrame start, end;
    private boolean expectsValue, carryingValue;
    private AbstractInsnNode lastInsn;

    public Block(LabelNode label, int idx) {
        this.idx = idx;
        this.label = label;
        this.insns = new ArrayList<>();
        this.frames = new HashMap<>();

        this.predecessors = new ArrayList<>();
        this.successors = new ArrayList<>();

        this.traps = new ArrayList<>();
        this.trapEnds = new ArrayList<>();
        this.trapHandlers = new ArrayList<>();
    }

    public boolean isInitialized(int local) {
        return !start.getLocal(local).isUninitialized();
    }

    public boolean deadEnd() {
        return defaultBlock == null;
    }

    public boolean ends() {
        var insn = insns.getLast();
        return insn.getOpcode() == Opcodes.GOTO
                || insn.getOpcode() == Opcodes.LOOKUPSWITCH
                || insn.getOpcode() == Opcodes.TABLESWITCH
                || ASMUtils.isReturn(insn) || insn.getOpcode() == Opcodes.ATHROW;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public void setExpectsValue() {
        this.expectsValue = true;
    }

    public void setCarryingValue() {
        this.carryingValue = true;
    }

    public boolean carryingValue() {
        return carryingValue;
    }

    public boolean expectsValue() {
        return expectsValue;
    }

    public Map<AbstractInsnNode, SimpleFrame> frames() {
        return frames;
    }

    public Frame<SimpleValue> frameAt(AbstractInsnNode insn) {
        return frames.get(insn);
    }

    public SimpleFrame start() {
        return start;
    }

    public Frame<SimpleValue> end() {
        return end;
    }

    public void setStart(SimpleFrame start) {
        this.start = start;
    }

    public void setEnd(SimpleFrame end) {
        this.end = end;
    }

    public AbstractInsnNode lastInsn() {
        return lastInsn;
    }

    public void setLastInsn(AbstractInsnNode insn) {
        this.lastInsn = insn;
    }

    public void vertex(Block dst) {
        successors.add(dst);
        dst.predecessors.add(this);
    }

    public Block defaultBlock() {
        return defaultBlock;
    }

    public void setDefaultBlock(Block defaultBlock) {
        this.defaultBlock = defaultBlock;
    }

    public List<Block> predecessors() {
        return predecessors;
    }

    public List<Block> successors() {
        return successors;
    }

    public LabelNode label() {
        return label;
    }

    public List<AbstractInsnNode> insns() {
        return insns;
    }

    public List<TryCatchBlockNode> traps() {
        return traps;
    }

    public List<TryCatchBlockNode> trapEnds() {
        return trapEnds;
    }

    public List<TryCatchBlockNode> trapHandlers() {
        return trapHandlers;
    }

    public boolean inTrap() {
        return !traps.isEmpty();
    }

    public boolean inTrapEnd() {
        return !trapEnds.isEmpty();
    }

    public boolean inTrapHandler() {
        return !trapHandlers.isEmpty();
    }

    @Override
    public String toString() {
        return "block" + idx;
    }
}
