package org.starset.dfsui.network;

import net.minecraft.network.FriendlyByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * 长度前缀 UTF-8 字符串的编解码，与 {@code deltaforcestrike} 服务端插件频道的
 * 二进制格式对齐（{@code int 长度 + UTF-8 字节}）。所有 payload 共用。
 */
public final class StringCodec {

    /**
     * 单条文本长度上限。Bukkit plugin message 整帧硬上限是 32767 字节，
     * 单串上限取 32767 贴着平台红线之下。
     */
    public static final int MAX_LENGTH = 32767;

    private StringCodec() {
    }

    public static void write(FriendlyByteBuf buf, String value) {
        if (value == null) {
            value = "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static String read(FriendlyByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > MAX_LENGTH) {
            // 畸形长度：不能像“返回空串但已消费 4 字节长度头”那样让后续字段
            // 全部错位解出垃圾快照。可安全跳过时跳过该字段，否则抛错让
            // decode 干净失败（断连），绝不错位续读。
            if (len > buf.readableBytes()) {
                throw new IndexOutOfBoundsException("Malformed string length: " + len);
            }
            if (len > 0) {
                buf.skipBytes(len);
            }
            return "";
        }
        if (buf.readableBytes() < len) {
            throw new IndexOutOfBoundsException("Truncated string: need " + len + " bytes");
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
