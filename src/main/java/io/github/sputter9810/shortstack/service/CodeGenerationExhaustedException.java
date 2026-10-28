package io.github.sputter9810.shortstack.service;

public class CodeGenerationExhaustedException extends RuntimeException {

    public CodeGenerationExhaustedException(Throwable cause) {
        super("Unable to allocate a short code after 5 insertion attempts.", cause);
    }
}
