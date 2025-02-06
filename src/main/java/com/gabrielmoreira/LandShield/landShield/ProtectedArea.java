package com.gabrielmoreira.LandShield.landShield;

import org.bukkit.Location;

import java.util.UUID;

public class ProtectedArea {

    private final UUID owner;
    private final int xMin, xMax, zMin, zMax, y;
    private long lastLogout;

    public ProtectedArea(UUID owner, int xMin, int xMax, int zMin, int zMax, int y, long lastLogout) {
        this.owner = owner;
        this.xMin = xMin;
        this.xMax = xMax;
        this.zMin = zMin;
        this.zMax = zMax;
        this.y = y;
        this.lastLogout = lastLogout;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getxMin() {
        return xMin;
    }

    public int getxMax() {
        return xMax;
    }

    public int getzMin() {
        return zMin;
    }

    public int getzMax() {
        return zMax;
    }

    public int getY() {
        return y;
    }

    public long getLastLogout() {
        return lastLogout;
    }

    public void setLastLogout(long lastLogout) {
        this.lastLogout = lastLogout;
    }

    public boolean isInside(Location loc) {
        return loc.getBlockX() >= xMin && loc.getBlockX() <= xMax
                && loc.getBlockZ() >= zMin && loc.getBlockZ() <= zMax
                && loc.getBlockY() == y;
    }
}
