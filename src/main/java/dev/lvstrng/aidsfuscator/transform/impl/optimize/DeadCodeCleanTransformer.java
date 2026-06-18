package dev.lvstrng.aidsfuscator.transform.impl.optimize;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.transform.Transformer;
import org.objectweb.asm.tree.LabelNode;

public class DeadCodeCleanTransformer extends Transformer {
    public DeadCodeCleanTransformer() {
        super("Clean Dead Code", "cleanDeadCode");
    }

    @Override
    public void transform(Context context) {
        for(var clazz : context.classes()) {
            for(var method : clazz.methods()) {
                var frames = method.frames(context);
                if(frames == null)
                    continue;

                for(var insn : method.insns()) {
                    var frame = frames.get(insn);
                    if(frame != null)
                        continue;

                    if(insn instanceof LabelNode)
                        continue;

                    method.insns().remove(insn);
                    markChange();
                }
            }
        }
    }
}
