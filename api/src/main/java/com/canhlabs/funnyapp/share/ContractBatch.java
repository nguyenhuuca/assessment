package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.share.exception.CustomException;

import java.util.ArrayList;
import java.util.List;

public class ContractBatch {
    private final List<ContractDSL<?>> checks = new ArrayList<>();

    public static ContractBatch start() {
        return new ContractBatch();
    }

    public <T extends Comparable<T>> ContractDSL<T> check(String fieldName, T value) {
        ContractDSL<T> check = ContractDSL.soft(fieldName, value);
        checks.add(check);
        return check;
    }

    public void validate() {
        List<String> allErrors = checks.stream()
                .flatMap(c -> c.getErrors().stream())
                .toList();

        if (!allErrors.isEmpty()) {
            CustomException.raiseErr(allErrors);
        }
    }
}
