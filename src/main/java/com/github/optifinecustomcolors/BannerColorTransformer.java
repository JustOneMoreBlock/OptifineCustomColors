package com.github.optifinecustomcolors;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class BannerColorTransformer implements IClassTransformer {
    private static final String HOOK_OWNER = "com/github/optifinecustomcolors/DyeColorHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !isLayeredColorMaskTexture(name, transformedName)) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean changed = false;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;
            if (!isLoadTextureMethod(method)) {
                continue;
            }

            injectReload(method);
            changed |= replaceMapColorReads(method);
        }

        if (!changed) {
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isLayeredColorMaskTexture(String name, String transformedName) {
        return "bmc".equals(name)
            || "bmc".equals(transformedName)
            || "net.minecraft.client.renderer.texture.LayeredColorMaskTexture".equals(transformedName)
            || "net.minecraft.client.renderer.texture.LayeredColorMaskTexture".equals(name);
    }

    private static boolean isLoadTextureMethod(MethodNode method) {
        return ("a".equals(method.name) && "(Lbni;)V".equals(method.desc))
            || ("loadTexture".equals(method.name)
                && "(Lnet/minecraft/client/resources/IResourceManager;)V".equals(method.desc));
    }

    private static void injectReload(MethodNode method) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            HOOK_OWNER,
            "reload",
            "(Ljava/lang/Object;)V",
            false
        ));
        method.instructions.insert(instructions);
    }

    private static boolean replaceMapColorReads(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (!(node instanceof FieldInsnNode)) {
                continue;
            }

            FieldInsnNode field = (FieldInsnNode) node;
            if (field.getOpcode() == Opcodes.GETFIELD && "I".equals(field.desc) && isMapColorField(field)) {
                method.instructions.set(field, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOK_OWNER,
                    "getBannerColor",
                    "(Ljava/lang/Object;)I",
                    false
                ));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isMapColorField(FieldInsnNode field) {
        boolean ownerMatches = "arn".equals(field.owner)
            || "net/minecraft/block/material/MapColor".equals(field.owner);
        boolean nameMatches = "L".equals(field.name)
            || "colorValue".equals(field.name)
            || "field_76291_p".equals(field.name);
        return ownerMatches && nameMatches;
    }
}
