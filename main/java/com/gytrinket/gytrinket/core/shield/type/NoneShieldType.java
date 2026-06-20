package com.gytrinket.gytrinket.core.shield.type;


public class NoneShieldType implements IShieldType {

    @Override
    public String getName() {
        return "none";
    }

    @Override
    public boolean isCompatible() {
        return true;
    }
}