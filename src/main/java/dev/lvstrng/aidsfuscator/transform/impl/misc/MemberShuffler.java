package dev.lvstrng.aidsfuscator.transform.impl.misc;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.transform.Transformer;

import java.util.Collections;

public class MemberShuffler extends Transformer {


    public MemberShuffler() {
        super("Shuffle Members", "MemberShuffler");
    }

    @Override
    public void transform(Context context) {
        for (var Clazz : context.classes()) {
            var cn = Clazz.core();
            Collections.shuffle(cn.methods);
            Collections.shuffle(cn.fields);
        }
    }
}
