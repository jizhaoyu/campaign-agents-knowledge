package com.enterprise.agentplatform.common.api;

import com.enterprise.agentplatform.common.exception.BusinessException;

public final class PageRequestValidator {

    private PageRequestValidator() {
    }

    public static boolean isPaged(Integer page, Integer size) {
        return page != null || size != null;
    }

    public static Params resolve(Integer page, Integer size, int defaultSize) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? defaultSize : size;
        if (resolvedPage < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page 不能小于 0");
        }
        if (resolvedSize < 1 || resolvedSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "size 必须在 1 到 100 之间");
        }
        return new Params(resolvedPage, resolvedSize);
    }

    public record Params(int page, int size) {
    }
}
