package com.trading.aiscalptrader.domain.enums;

public enum OptionType {
    CE, PE;

    public boolean isCall() {
        return this == CE;
    }
}
