package org.serialthreads.transformer.code;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.Opcodes.DALOAD;
import static org.objectweb.asm.Opcodes.DASTORE;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DCONST_1;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Type.DOUBLE;
import static org.objectweb.asm.Type.DOUBLE_TYPE;

/**
 * Code for double values.
 */
public class DoubleValueCode extends AbstractValueCode {
    public DoubleValueCode() {
        super(DOUBLE_TYPE, "Double", DLOAD, DSTORE, DALOAD, DASTORE, DCONST_0, DRETURN);
    }

    @Override
    public boolean isResponsibleFor(Type type) {
        return type != null && type.getSort() == DOUBLE;
    }

    @Override
    public AbstractInsnNode push(@Nullable Object value) {
        double d = (Double) requireNonNull(value);

        if (d == 0D) {
            return new InsnNode(DCONST_0);
        } else if (d == 1D) {
            return new InsnNode(DCONST_1);
        }

        return new LdcInsnNode(d);
    }
}
