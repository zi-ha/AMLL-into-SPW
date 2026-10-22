package io.github.gaboron.amllspw.host

import com.xuncorp.spw.workshop.api.WorkshopApi
import org.objectweb.asm.*
import java.io.File
import java.util.jar.JarFile

/** Resolves original source trace markers after obfuscation; refuses missing/ambiguous call sites. */
internal data class NativeLyricsTarget(val owner: String, val method: String, val caller: String, val parameters: List<String>) {
    /**
     * The single Composable inside the container that draws the lyric lines themselves.
     * Replacing it leaves the container, its padding and the player page untouched, so the page
     * keeps its own background and the layer drawn here can stay fully transparent.
     */
    internal data class Renderer(val owner: String, val method: String, val parameters: List<String>) {
        /**
         * The same entry point as a plain JVM method, so the plugin can run the host's own renderer
         * with a substituted argument list. Host composables are ordinary static methods with the
         * composer appended, which is exactly what the hook framework calls under the hood.
         */
        fun reflective(): java.lang.reflect.Method? = runCatching {
            val loader = WorkshopApi::class.java.classLoader
            val types = parameters.map { typeOf(it, loader) } + listOf(
                androidx.compose.runtime.Composer::class.java,
                java.lang.Integer.TYPE, java.lang.Integer.TYPE)
            Class.forName(owner, false, loader).getDeclaredMethod(method, *types.toTypedArray())
                .apply { isAccessible = true }
        }.getOrElse { error ->
            // The host swallows plugin startup stack traces, so leave one line naming the exact
            // entry point that could not be resolved; it is the first thing to check after an update.
            System.err.println("AMLL: 无法反射解析 ${owner}#${method}：${error.javaClass.simpleName}: ${error.message}")
            null
        }

        /** ASM reports array parameters as `java.lang.String[]`; reflection needs the array class. */
        private fun typeOf(name: String, loader: ClassLoader): Class<*> = when {
            name.endsWith("[]") ->
                java.lang.reflect.Array.newInstance(typeOf(name.dropLast(2), loader), 0).javaClass
            name == "boolean" -> java.lang.Boolean.TYPE
            name == "byte" -> java.lang.Byte.TYPE
            name == "char" -> java.lang.Character.TYPE
            name == "short" -> java.lang.Short.TYPE
            name == "int" -> java.lang.Integer.TYPE
            name == "long" -> java.lang.Long.TYPE
            name == "float" -> java.lang.Float.TYPE
            name == "double" -> java.lang.Double.TYPE
            else -> Class.forName(name, false, loader)
        }
    }

    companion object {
        private const val PLAYER_MARKER = "com.xuncorp.voxzen.ui.player.component.lyrics.PlayerLyrics (PlayerLyrics.kt:"
        private const val CONTAINER_MARKER = "com.xuncorp.spc.lyrics.component.LyricsContainer (LyricsContrainer.kt:"
        private const val COMPOSER = "androidx.compose.runtime.Composer"
        private const val MODIFIER = "androidx.compose.ui.Modifier"
        private const val FUNCTION0 = "kotlin.jvm.functions.Function0"
        // The only host type in the line renderer signature whose name survives obfuscation.
        private const val KARAOKE = "com.xuncorp.spc.lyrics.KaraokeCompatStrategy"

        private fun hostArchive(): File =
            File(WorkshopApi::class.java.protectionDomain.codeSource.location.toURI())

        /**
         * Finds the line renderer by shape, never by name: a Composable call inside the container
         * that receives the karaoke strategy and the layout modifier. Returns null when the shape
         * is missing or ambiguous, which makes the plugin refuse to start instead of covering the
         * native lyrics with a colour block.
         */
        fun renderer(container: String, host: File = hostArchive()): Renderer? {
            if (!host.isFile) return null
            return runCatching {
                JarFile(host).use { jar ->
                    val entry = jar.getJarEntry(container.replace('.', '/') + ".class") ?: return null
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }
                    val calls = LinkedHashSet<Renderer>()
                    ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?,
                            exceptions: Array<out String>?): MethodVisitor =
                            object : MethodVisitor(Opcodes.ASM9) {
                                override fun visitMethodInsn(opcode: Int, owner: String, name: String,
                                    descriptor: String, isInterface: Boolean) {
                                    if (Type.getReturnType(descriptor) != Type.VOID_TYPE) return
                                    val types = Type.getArgumentTypes(descriptor)
                                    val composer = types.indexOfFirst { it.className == COMPOSER }
                                    if (composer < 0) return
                                    val business = types.take(composer)
                                    if (business.count { it.className == KARAOKE } != 1) return
                                    if (business.count { it.className == MODIFIER } != 1) return
                                    if (owner == container.replace('.', '/')) return
                                    calls += Renderer(owner.replace('/', '.'), name, business.map { it.className })
                                }
                            }
                    }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                    calls.singleOrNull()
                }
            }.getOrNull()
        }

        fun discover(host: File = File(WorkshopApi::class.java.protectionDomain.codeSource.location.toURI())): NativeLyricsTarget {
            require(host.isFile) { "宿主代码不是可读取的 JAR，无法确认原生歌词入口" }
            return JarFile(host).use { jar ->
                val playerClasses = mutableListOf<ClassReader>()
                val containerClasses = mutableSetOf<String>()
                for (entry in jar.entries()) {
                    if (!entry.name.endsWith(".class")) continue
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }
                    // Search only the two stable compiler source markers, never guessed class names.
                    val strings = String(bytes, Charsets.ISO_8859_1)
                    if (PLAYER_MARKER in strings) playerClasses += ClassReader(bytes)
                    if (CONTAINER_MARKER in strings) containerClasses += ClassReader(bytes).className
                }
                // Matched by shape, not by argument index: the host may add or reorder parameters
                // between releases, and a hard-coded Composer slot turned that into a startup
                // failure. The invariants we do need are the two callbacks and the layout modifier.
                val candidates = mutableListOf<NativeLyricsTarget>()
                val signatures = mutableListOf<String>()
                val skipped = mutableListOf<String>()
                playerClasses.forEach { reader ->
                    reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor =
                            object : MethodVisitor(Opcodes.ASM9) {
                                var lastOpcode = -1
                                override fun visitInsn(opcode: Int) { lastOpcode = opcode }
                                override fun visitVarInsn(opcode: Int, variable: Int) { lastOpcode = opcode }
                                override fun visitIntInsn(opcode: Int, operand: Int) { lastOpcode = opcode }
                                override fun visitLdcInsn(value: Any?) { lastOpcode = Opcodes.LDC }
                                override fun visitMethodInsn(opcode: Int, owner: String, method: String, desc: String, isInterface: Boolean) {
                                    val args = Type.getArgumentTypes(desc).toList()
                                    val composer = args.indexOfFirst { it.className == COMPOSER }
                                    val business = if (composer >= 0) args.take(composer) else emptyList()
                                    if (owner in containerClasses && composer >= 0 &&
                                        Type.getReturnType(desc) == Type.VOID_TYPE &&
                                        business.count { it.className == MODIFIER } == 1 &&
                                        business.count { it.className == FUNCTION0 } >= 2) {
                                        val signature = "${owner.replace('/', '.')}#$method(" +
                                            business.joinToString(",") { it.className } + ")"
                                        // ICONST_0 right before the call is the empty default mask:
                                        // every argument is passed explicitly, so replacing the call
                                        // cannot silently change a default.
                                        if (lastOpcode == Opcodes.ICONST_0) {
                                            candidates += NativeLyricsTarget(owner.replace('/', '.'), method,
                                                reader.className.replace('/', '.'), business.map { it.className })
                                            signatures += signature
                                        } else {
                                            skipped += signature
                                        }
                                    }
                                    lastOpcode = opcode
                                }
                            }
                    }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                }
                require(candidates.isNotEmpty()) {
                    buildString {
                        append("无法确认 PlayerLyrics → LyricsContainer 调用")
                        if (skipped.isNotEmpty()) {
                            append("；以下调用使用了省略参数，无法安全替换：").append(skipped.joinToString(" / "))
                        }
                    }
                }
                require(candidates.size == 1) {
                    "无法唯一确认 PlayerLyrics → LyricsContainer 调用（找到 ${candidates.size} 个：" +
                        signatures.joinToString(" / ") + "）"
                }
                candidates.single().also { target ->
                    val metadata = mutableListOf<String>()
                    ClassReader(jar.getInputStream(jar.getJarEntry(target.owner.replace('.', '/') + ".class"))).accept(object : ClassVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? =
                            if (descriptor != "Lkotlin/Metadata;") null else object : AnnotationVisitor(Opcodes.ASM9) {
                                override fun visitArray(name: String): AnnotationVisitor? = if (name != "d2") null else object : AnnotationVisitor(Opcodes.ASM9) {
                                    override fun visit(name: String?, value: Any?) { if (value is String) metadata += value }
                                }
                            }
                    }, ClassReader.SKIP_CODE)
                    val lineTime = metadata.indexOf("onLineTime")
                    val time = metadata.indexOf("onTime")
                    require(lineTime >= 0 && time > lineTime && time < metadata.indexOf("onClick")) {
                        "宿主歌词时钟参数顺序发生变化，无法安全绑定播放进度"
                    }
                }
            }
        }
    }
}
