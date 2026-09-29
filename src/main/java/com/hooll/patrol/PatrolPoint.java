package com.hooll.patrol;

public class PatrolPoint {
    public String name = "";
    public String dimension = "";
    public int x;
    public int y;
    public int z;

    public PatrolPoint() {
    }

    public PatrolPoint(String name, String dimension, int x, int y, int z) {
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
