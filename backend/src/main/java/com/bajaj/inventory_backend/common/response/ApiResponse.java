
package com.bajaj.inventory_backend.common.response;

import java.util.List;

public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private List<String> errors;

    public ApiResponse() {
    }

    public ApiResponse(
            boolean success,
            String message,
            T data,
            List<String> errors) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }

    public static <T> ApiResponse<T> success(
            String message, T data) {
        return new ApiResponse<>(true, message, data, List.of());
    }

    public static <T> ApiResponse<T> error(
            String message, List<String> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public List<String> getErrors() {
        return errors;
    }
}
