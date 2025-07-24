package com.canhlabs.funnyapp.utils;

import com.canhlabs.funnyapp.exception.CustomException;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@Getter
public class ContractDSL<T extends Comparable<T>> {

    private final String fieldName;
    private final T value;
    private final boolean immediateThrow;

    private final List<String> errors = new ArrayList<>();

    private ContractDSL(String fieldName, T value, boolean immediateThrow) {
        this.fieldName = fieldName;
        this.value = value;
        this.immediateThrow = immediateThrow;
    }

    public static <T extends Comparable<T>> ContractDSL<T> single(String fieldName, T value) {
        return new ContractDSL<>(fieldName, value, true);
    }

    public static <T extends Comparable<T>> ContractDSL<T> soft(String fieldName, T value) {
        return new ContractDSL<>(fieldName, value, false);
    }

    public ContractDSL<T> must(Predicate<T> predicate, String message) {
        if (!predicate.test(value)) {
            String fullMsg = fieldName + ": " + message;
            if (immediateThrow) {
                CustomException.raiseErr(fullMsg);
            } else {
                errors.add(fullMsg);
            }
        }
        return this;
    }

    public ContractDSL<T> mustNotNull(String message) {
        return must(Objects::nonNull, message);
    }

    public ContractDSL<T> mustMin(T min, String message) {
        return must(v -> v != null && v.compareTo(min) >= 0, message);
    }

    public ContractDSL<T> mustMax(T max, String message) {
        return must(v -> v != null && v.compareTo(max) <= 0, message);
    }

    @SuppressWarnings("unchecked")
    public ContractDSL<String> mustMatchRegex(String regex, String message) {
        return ((ContractDSL<String>) this).must(val -> val != null && val.matches(regex), message);
    }

    @SuppressWarnings("unchecked")
    public ContractDSL<String> mustInEnum(Class<? extends Enum<?>> enumClass, String message) {
        return ((ContractDSL<String>) this).must(val -> {
            if (val == null) return false;
            for (Enum<?> e : enumClass.getEnumConstants()) {
                if (e.name().equals(val)) return true;
            }
            return false;
        }, message);
    }

}
