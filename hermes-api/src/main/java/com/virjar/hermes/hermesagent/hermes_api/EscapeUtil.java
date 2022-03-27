package com.virjar.hermes.hermesagent.hermes_api;

import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Created by virjar on 2018/8/24.
 */

public class EscapeUtil {
    private static Set<Integer> unEncodeCharacters = Sets.newHashSet();

    static {
        for (int i = 'a'; i <= 'z'; i++) {
            unEncodeCharacters.add(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            unEncodeCharacters.add(i);
        }
        for (int i = '0'; i <= '9'; i++) {
            unEncodeCharacters.add(i);
        }
        unEncodeCharacters.add((int) '*');
        unEncodeCharacters.add((int) '@');
        unEncodeCharacters.add((int) '-');
        unEncodeCharacters.add((int) '_');
        unEncodeCharacters.add((int) '+');
        unEncodeCharacters.add((int) '.');
        unEncodeCharacters.add((int) '/');
    }

    /**
     * 符合js URL编码规范的解密方法
     *
     * @param src 待解码输入
     * @return 解码结果
     */
    public static String unescape(String src) {
        StringBuilder tmp = new StringBuilder();
        tmp.ensureCapacity(src.length());
        int lastPos = 0, pos;
        char ch;
        while (lastPos < src.length()) {
            pos = src.indexOf("%", lastPos);
            if (pos == lastPos) {
                if (src.charAt(pos + 1) == 'u') {
                    ch = (char) Integer.parseInt(src.substring(pos + 2, pos + 6), 16);
                    tmp.append(ch);
                    lastPos = pos + 6;
                } else {
                    ch = (char) Integer.parseInt(src.substring(pos + 1, pos + 3), 16);
                    tmp.append(ch);
                    lastPos = pos + 3;
                }
            } else {
                if (pos == -1) {
                    tmp.append(src.substring(lastPos));
                    lastPos = src.length();
                } else {
                    tmp.append(src.substring(lastPos, pos));
                    lastPos = pos;
                }
            }
        }
        return tmp.toString();
    }

    /**
     * 模仿JS的escape函数,注意和java中的URLEncoder表现会不一样,这个函数完全按照js的编码标准来实现,具体看w3c描述
     * <a href="http://www.w3school.com.cn/jsref/jsref_escape.asp">该方法不会对 ASCII 字母和数字进行编码，也不会对下面这些 ASCII 标点符号进行编码： * @ -
     * _ + . / 。其他所有的字符都会被转义序列替换。</a>
     *
     * @param src 待编码字符串
     * @return 编码结果
     */
    public static String escape(String src) {
        int i;
        char j;
        StringBuilder tmp = new StringBuilder();
        tmp.ensureCapacity(src.length() * 6);

        for (i = 0; i < src.length(); i++) {
            j = src.charAt(i);
            if (unEncodeCharacters.contains((int) j)) {
                tmp.append(j);
            } else if (j < 256) {
                tmp.append("%");
                if (j < 16) {
                    tmp.append("0");
                }
                tmp.append(Integer.toString(j, 16).toUpperCase());
            } else {
                tmp.append("%u");
                String temp = Integer.toString(j, 16).toUpperCase();
                for (int k = 0; k < 4 - temp.length(); k++) {
                    tmp.append("0");// 补满4️位
                }
                tmp.append(temp);
            }
        }
        return tmp.toString();
    }
}
