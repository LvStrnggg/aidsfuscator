package dev.lvstrng.aidsfuscator.context.asm;

import dev.lvstrng.aidsfuscator.naming.Mappings;
import dev.lvstrng.aidsfuscator.utils.MemberUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

public class RemapperImpl extends Remapper {
    public RemapperImpl() {
        super(Opcodes.ASM9);
    }

    @Override
    public String map(String internalName) {
        if(Mappings.CLASS.containsOldTemp(internalName))
            return super.map(Mappings.CLASS.retrieveTemp(internalName).value());

        return super.map(internalName);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        var id = MemberUtils.fullMethod(owner, name, descriptor);
        if(Mappings.METHOD.containsOldTemp(id))
            return super.mapMethodName(owner, Mappings.METHOD.retrieveTemp(id).value(), descriptor);

        return super.mapMethodName(owner, name, descriptor);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        var id = MemberUtils.fullField(owner, name, descriptor);

        if(Mappings.FIELD.containsOldTemp(id))
            return super.mapFieldName(owner, Mappings.FIELD.retrieveTemp(id).value(), descriptor);

        return super.mapFieldName(owner, name, descriptor);
    }
}
