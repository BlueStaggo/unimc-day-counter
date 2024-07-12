package io.bluestaggo.unimcdaycounter;

import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.StringTag;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {
    private static long dayCounter = -1;
    private static short indevTimeCounter;
    private static boolean indev;

    private Main() {
    }

    public static void main(String[] args) {
        File levelFile = null;
        if (args.length >= 1) {
            levelFile = new File(args[0]);
            if (!levelFile.exists() || levelFile.isDirectory()) {
                levelFile = null;
            }
        }

        if (levelFile == null) {
            FileDialog fileDialog = new FileDialog((Frame)null, "Select Minecraft level file");
            fileDialog.setFilenameFilter((dir, name) -> name.endsWith(".dat") || name.endsWith(".mclevel"));
            fileDialog.setMode(FileDialog.LOAD);
            fileDialog.setVisible(true);

            String fileName = fileDialog.getFile();
            if (fileName == null) return;

            levelFile = new File(fileDialog.getDirectory(), fileName);
            fileDialog.dispose();
        }

        if (!levelFile.exists() || levelFile.isDirectory()) {
            System.err.println("Failed to open file \"" + levelFile + "\"");
            return;
        }

        long interval = 5;
        if (args.length >= 2) {
            try {
                interval = Math.max(Long.parseLong(args[1]), 1);
            } catch (NumberFormatException ignored) {
            }
        }

        indev = levelFile.getName().endsWith(".mclevel");
        final File finalLevelFile = levelFile;

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(() -> {
            if (!printDay(finalLevelFile)) {
                executor.shutdown();
            }
        }, 0, interval, TimeUnit.SECONDS);
    }

    private static boolean printDay(File levelFile) {
        String worldName = levelFile.getParentFile().getName();
        long prevDay = dayCounter;
        long day;

        try {
            NamedTag rootTag = NBTUtil.read(levelFile);
            CompoundTag rootCompound = (CompoundTag) rootTag.getTag();

            if (!indev) {
                CompoundTag data = rootCompound.getCompoundTag("Data");
                if (data.get("LevelName") instanceof StringTag) {
                    worldName = data.getString("LevelName");
                }

                long time;
                if (data.get("DayTime") instanceof LongTag) {
                    time = data.getLong("DayTime");
                } else {
                    time = data.getLong("Time");
                }
                day = time / 24000L;
            } else {
                worldName = levelFile.getName().replaceAll(".mclevel$", "");

                CompoundTag environment = rootCompound.getCompoundTag("Environment");
                short timeOfDay = environment.getShort("TimeOfDay");
                if (timeOfDay < indevTimeCounter) {
                    dayCounter++;
                }
                indevTimeCounter = timeOfDay;
                day = dayCounter;
            }
        } catch (IOException | ClassCastException e) {
            System.err.println("Failed to read \"" + levelFile + "\"");
            e.printStackTrace();
            return false;
        }

        if (day == prevDay) return true;
        dayCounter = day;

        String message = worldName + " - ";
        if (!indev) {
            message += "Day " + day;
        } else {
            message += day + (day == 1 ? " day" : " days") + " counted";
        }

        Toolkit.getDefaultToolkit().beep();
        System.out.println("==========");
        System.out.println(message);

        return true;
    }
}
