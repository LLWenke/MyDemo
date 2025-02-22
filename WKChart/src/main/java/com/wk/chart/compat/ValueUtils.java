package com.wk.chart.compat;

import androidx.annotation.Nullable;

import com.wk.chart.entry.QuantizationEntry;
import com.wk.chart.entry.RateEntry;
import com.wk.chart.entry.ValueEntry;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ValueUtils {
    private static final Long[] values;
    private static final String[] units;
    private static final String[] zeros;

    private ValueUtils() {
    }

    static {
        values = new Long[]{1000L, 1000000L, 1000000000L, 1000000000000L};
        units = new String[]{"K", "M", "B", "T"};
        zeros = new String[13];
        zeros[0] = "0.";
        zeros[1] = "0.0";
        zeros[2] = "0.00";
        zeros[3] = "0.000";
        zeros[4] = "0.0000";
        zeros[5] = "0.00000";
        zeros[6] = "0.000000";
        zeros[7] = "0.0000000";
        zeros[8] = "0.00000000";
        zeros[9] = "0.000000000";
        zeros[10] = "0.0000000000";
        zeros[11] = "0.00000000000";
        zeros[12] = "0.000000000000";
    }

    /**
     * 构建精度值
     *
     * @param entry 源数据
     * @param scale 精度
     */
    public static void buildScaleValue(@NotNull ValueEntry entry, int scale) {
        BigDecimal decimal = bigDecimal(entry.source);
        decimal = decimal.setScale(scale, RoundingMode.DOWN);
        entry.scale = scale;
        entry.text = decimal.toPlainString();
        entry.value = decimal.floatValue();
        entry.result = decimal.unscaledValue().longValue();
    }

    /**
     * 构建精度值
     *
     * @param entry  源数据
     * @param result 整数值
     * @param scale  精度
     */
    public static void buildScaleValue(@NotNull ValueEntry entry, long result, int scale) {
        entry.scale = scale;
        entry.text = buildText(result, scale, false);
        entry.value = (float) buildValue(result, scale);
        entry.result = result;
        entry.source = entry.text;
    }

    /**
     * 构建精度值
     *
     * @param result 整数值
     * @param scale  精度
     * @return 精度值实例
     */
    public static ValueEntry buildScaleValue(long result, int scale) {
        ValueEntry entry = new ValueEntry();
        buildScaleValue(entry, result, scale);
        return entry;
    }

    /**
     * 构建ValueEntry中的Text属性值
     *
     * @param result             传入的result值
     * @param scale              精度
     * @param stripTrailingZeros 去除无用的0（如：2.4560->2.456）
     * @return 返回构建后ValueEntry中的Text属性值
     */
    public static String buildText(long result, int scale, boolean stripTrailingZeros) {
        if (scale <= 0) return String.valueOf(result);
        StringBuilder text = new StringBuilder(String.valueOf(result));
        int length;
        int offset;
        if (result < 0) {
            length = text.length() - 1;
            offset = 1;
        } else {
            length = text.length();
            offset = 0;
        }
        if (scale < length) {
            text.insert(length - scale + offset, '.');
        } else {
            int zeroCount = scale - length;
            text.insert(offset, zeroCount < zeros.length ? zeros[zeroCount] : zeros[zeros.length - 1]);
        }
        if (!stripTrailingZeros) return text.toString();
        length = text.length();
        offset = length;
        for (int i = length - 1; i > 0; i--) {
            char cr = text.charAt(i);
            if ('0' == cr) {
                offset = i;
            } else if ('.' == cr) {
                offset = i;
                break;
            } else {
                break;
            }
        }
        if (offset < length) {
            text.delete(offset, length);
        }
        return text.toString();
    }

    /**
     * 构建ValueEntry中的value属性值
     *
     * @param result 传入的result值
     * @param scale  精度
     * @return 返回构建后ValueEntry中的value属性值
     */
    public static double buildValue(long result, int scale) {
        if (scale > 0) {
            return result / pow10(scale);
        }
        return result;
    }

    /**
     * 构建ValueEntry中的value属性值
     * ！！！此方法慎用，可能会导致精度丢失 ！！！
     *
     * @param value 传入的value值
     * @param scale 精度
     * @return 返回构建后ValueEntry中的value属性值
     */
    public static long buildResult(double value, int scale) {
        if (scale > 0) {
            return (long) (value * pow10(scale));
        }
        return (long) value;
    }

    /**
     * 格式化value(可选：比率转换、量化转换)
     * ！！！比率精度和量化精度不宜超过数值本身的精度，否则可能导致精度丢失 ！！！
     *
     * @param result             传入的result值
     * @param scale              精度
     * @param rate               比率(传入NULL不作比率转换)
     * @param quantization       量化实例(传入NULL不作量化转换)
     * @param stripTrailingZeros 去除无用的0（如：2.4560->2.456）
     * @return 返回格式化后的字符串
     */
    public static String rateFormat(long result, int scale, @Nullable RateEntry rate,
                                    @Nullable QuantizationEntry quantization, boolean stripTrailingZeros) {
        long scalePow = (long) pow10(scale);
        int valueScale = scale;
        String sign = "";
        String unit = "";
        //比率转换
        if (null != rate && rate.isSet()) {
            long rateResult = (long) (rate.getRate() * scalePow);
            result = scaleMultiply(result, rateResult, scale);
            valueScale = rate.getScale();
            sign = rate.getSign();
        }
        //量化转换
        if (null != quantization && result > (quantization.getMinFormatNum() * scalePow)) {
            valueScale = quantization.getScale();
            for (int i = values.length - 1; i >= 0; i--) {
                long num = values[i] * scalePow;
                if (result >= num) {
                    result = scaleDivide(result, num, scale);
                    unit = units[i];
                    break;
                }
            }
        }
        //校准结果精度值
        if (scale > valueScale) {
            result /= (long) pow10(scale - valueScale);
        } else if (scale < valueScale) {
            result *= (long) pow10(valueScale - scale);
        }
        //生成格式化后的字符串
        return sign.concat(buildText(result, valueScale, stripTrailingZeros)).concat(unit);
    }

    /**
     * 字符串转BigDecimal
     */
    public static BigDecimal bigDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 精度乘法
     *
     * @param result1 数值1
     * @param result2 数值2
     * @param scale   精度
     * @return 乘法结果
     */
    public static long scaleMultiply(long result1, long result2, int scale) {
        if (scale > 0) {
            return (long) (result1 * result2 / pow10(scale));
        }
        return result1 * result2;
    }

    /**
     * 精度除法
     *
     * @param result1 数值1
     * @param result2 数值2
     * @param scale   精度
     * @return 除法结果
     */
    public static long scaleDivide(long result1, long result2, int scale) {
        if (scale > 0) {
            return (long) (result1 * pow10(scale) / result2);
        }
        return result1 / result2;
    }

    /**
     * 计算10的幂
     *
     * @param pow 幂
     * @return 计算幂值
     */
    public static double pow10(int pow) {
        return Math.pow(10, pow);
    }
}
