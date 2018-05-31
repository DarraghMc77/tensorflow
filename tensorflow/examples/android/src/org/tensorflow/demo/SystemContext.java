package org.tensorflow.demo;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;

import javax.annotation.Nonnull;

public class SystemContext {

    private int batteryLevel;
    private int cpuUsage;
    private float memoryUsage;

    private static class SystemContextHolder {
        public static final SystemContext instance = new SystemContext();
    }

    @Nonnull
    protected static SystemContext getInstance() {
        return SystemContextHolder.instance;
    }


    private SystemContext() {

    }

    protected synchronized void UpdateSystemContext(Context context) {
        cpuUsage = readCPUinfo();
        memoryUsage = readUsage();
    }

    private float readUsage() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            String[] toks = load.split(" +");  // Split on one or more spaces

            long idle1 = Long.parseLong(toks[4]);
            long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            try {
                Thread.sleep(360);
            } catch (Exception e) {}

            reader.seek(0);
            load = reader.readLine();
            reader.close();

            toks = load.split(" +");

            long idle2 = Long.parseLong(toks[4]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            return (float)(cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return 0;
    }

    private int readCPUinfo() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream("/proc/stat")), 1000);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("cpu")) {

                    final String[] tokens = line.split(" +");
                    Long idle = Long.parseLong(tokens[4]);
                    Long total = Long.parseLong(tokens[1])
                            + Long.parseLong(tokens[2])
                            + Long.parseLong(tokens[3]) + idle
                            + Long.parseLong(tokens[5])
                            + Long.parseLong(tokens[6])
                            + Long.parseLong(tokens[7]);
                    return (int) (100 - idle * 100 / total);
                }
                reader.close();
            }

        } catch (Exception e) {
            Log.e("simplicity", e.toString());
        }
        return 0;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public int getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(int cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public float getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(float memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

}
