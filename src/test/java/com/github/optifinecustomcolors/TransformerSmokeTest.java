package com.github.optifinecustomcolors;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class TransformerSmokeTest {
    public static void main(String[] args) throws Exception {
        byte[] original = Files.readAllBytes(Paths.get(args[0]));
        byte[] transformed = new BannerColorTransformer().transform("bmc", "bmc", original);

        ClassNode classNode = new ClassNode();
        new ClassReader(transformed).accept(classNode, 0);

        int reloadCalls = 0;
        int bannerColorCalls = 0;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;
            for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
                if (!(node instanceof MethodInsnNode)) {
                    continue;
                }

                MethodInsnNode call = (MethodInsnNode) node;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && "com/github/optifinecustomcolors/DyeColorHooks".equals(call.owner)) {
                    if ("reload".equals(call.name)) {
                        reloadCalls++;
                    } else if ("getBannerColor".equals(call.name)) {
                        bannerColorCalls++;
                    }
                }
            }
        }

        if (reloadCalls != 1 || bannerColorCalls != 1) {
            throw new IllegalStateException(
                "Expected one reload and one banner color hook, got reload="
                    + reloadCalls + ", bannerColor=" + bannerColorCalls
            );
        }

        System.out.println("Transformer smoke test passed");
    }
}
