package net.phylax.credible.utils;

/**
 * Result<S, F> is the type used for returning and propagating errors. 
 * @param <S> The type of the success value
 * @param <F> The type of the failure value
 */
public class Result<S, F> {
    private final S success;
    private final F failure;
    
    private Result(S success, F failure) {
        this.success = success;
        this.failure = failure;
    }
    
    public static <S, F> Result<S, F> success(S value) {
        return new Result<>(value, null);
    }
    
    public static <S, F> Result<S, F> failure(F error) {
        return new Result<>(null, error);
    }
    
    public boolean isSuccess() {
        return success != null;
    }
    
    /**
     * Unwraps the success value.
     * @return The success result object
     * @throws IllegalStateException if the result is not a success
     */
    public S getSuccess() {
        if (!isSuccess()) throw new IllegalStateException("Not a success");
        return success;
    }
    
    /**
     * Unwraps the failure value.
     * @throws IllegalStateException if the result is not a failure
     * @return
     */
    public F getFailure() {
        if (isSuccess()) throw new IllegalStateException("Not a failure");
        return failure;
    }
}
