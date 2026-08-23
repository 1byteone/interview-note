package com.passage.strategy;

/**
 * ImageAcquisitionException - 图片获取异常
 *
 * 图片获取过程中可能出现的异常封装。
 * 当所有图片获取策略都无法成功获取图片时，抛出此异常。
 *
 * 与 ImageContext 的降级机制配合使用：
 * - 各策略在 generateImage() 中抛出此异常
 * - ImageContext 捕获此异常后触发降级
 * - 最终兜底（Picsum）不抛异常，保证 100% 返回
 *
 * @author AI-Passage-Creator
 */
public class ImageAcquisitionException extends RuntimeException {

    /**
     * 构造方法
     *
     * @param message 异常描述信息
     */
    public ImageAcquisitionException(String message) {
        super(message);
    }

    /**
     * 构造方法（带原始异常）
     *
     * @param message 异常描述信息
     * @param cause   原始异常
     */
    public ImageAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}