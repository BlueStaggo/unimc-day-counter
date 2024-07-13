package io.bluestaggo.unimcdaycounter;

import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.StringTag;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {
    private static final String[] help = {
            "Usage: java -jar unimcdaycounter.jar [<file>] [options]",
            "File must be level.dat or an Indev .mclevel or leave blank to open a chooser dialog.",
            "Options:",
            "    -h --help                  Display this help screen",
            "    -i --interval=<integer>    Set check interval in seconds, min=1, default=5",
            "    -m --mute                  Mute beep/bell for new days",
            "       --motds=<csv file>      Set list of MOTDs to display every day",
            "       --motd-format=<format>  Format displayed MOTDs, use %MOTD% to reference the MOTD and %ESC% to use ANSI escape codes",
            "       --fresh-motds           Only display MOTDs on their associated day",
    };

    private static boolean beep;
    private static String motdFormat;
    private static boolean freshMotds;
    private static SortedMap<Long, String> motds;

    private static long dayCounter = -1;
    private static short indevTimeCounter;
    private static boolean indev;

    private Main() {
    }

    public static void main(String[] args) {
        if (hasFlag(args, 'h', "help")) {
            for (String line : help) {
                System.out.println(line);
            }
            return;
        }

        beep = !hasFlag(args, 'm', "mute");
        motdFormat = getArgValue(args, '\0', "motd-format");
        freshMotds = hasFlag(args, '\0', "fresh-motds");

        long interval = 5;
        String intervalString = getArgValue(args, 'i', "interval");
        if (intervalString != null) {
            try {
                interval = Math.max(Long.parseLong(intervalString), 1);
            } catch (NumberFormatException ignored) {
                System.err.println("Bad interval value \"" + intervalString + "\"");
            }
        }

        String motdsPath = getArgValue(args, '\0', "motds");
        if (motdsPath != null) loadMOTDs(motdsPath);

        File levelFile = null;
        if (args.length >= 1 && !args[0].startsWith("-")) {
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
            if (fileName == null) {
                System.err.println("No file chosen.");
                System.exit(1);
                return;
            }

            levelFile = new File(fileDialog.getDirectory(), fileName);
            fileDialog.dispose();
        }

        if (!levelFile.exists() || levelFile.isDirectory()) {
            System.err.println("Failed to open file \"" + levelFile + "\"");
            System.exit(1);
            return;
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
            System.err.println("\033[31mFailed to read \"" + levelFile + "\"");
            e.printStackTrace();
            System.err.print("\033[0m");
            System.exit(1);
            return false;
        }

        if (day == prevDay) return true;
        dayCounter = day;

        String message = "\033[33m" + worldName + "\033[90m - \033[36m";
        if (!indev) {
            message += "Day " + day + "\033[0m";
        } else {
            message += day + (day == 1 ? " day" : " days") + " counted\033[0m";
        }

        if (beep) Toolkit.getDefaultToolkit().beep();
        System.out.println("\033[90m==============================\033[0m");
        System.out.println(message);

        if (motds != null) {
            String motd = null;
            if (freshMotds) {
                motd = motds.get(day);
            } else {
                for (long key : motds.keySet()) {
                    if (day < key) continue;
                    motd = motds.get(key);
                }
            }

            if (motd != null) {
                if (motdFormat != null) {
                    motd = motdFormat
                            .replace("%ESC%", "\033")
                            .replace("%MOTD%", motd) + "\033[0m";
                }
                System.out.println(motd);
            }
        }

        return true;
    }

    private static boolean hasFlag(String[] args, char shortArg, String longArg) {
        return Arrays.stream(args)
                .anyMatch(s -> shortArg != '\0' && s.startsWith("-") && !s.startsWith("--") && s.chars().anyMatch(i -> i == shortArg)
                        || s.equals("--" + longArg));
    }

    private static String getArgValue(String[] args, char shortArg, String longArg) {
        if (shortArg == '=' || longArg.contains("=")) return null;
        return Arrays.stream(args)
                .filter(s -> shortArg != '\0' && s.startsWith("-" + shortArg + "=")
                        || s.startsWith("--" + longArg + "="))
                .findFirst()
                .map(s -> s.substring(s.indexOf('=') + 1))
                .orElse(null);
    }

    private static void loadMOTDs(String path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(path));
        } catch (IOException e) {
            return;
        }

        TreeMap<Long, String> motds = new TreeMap<>();
        for (String line : lines) {
            int comma1 = line.indexOf(',');
            if (comma1 == -1) continue;
            int comma2 = line.indexOf(',', comma1 + 1);

            long day;
            String message;

            try {
                day = Long.parseLong(line.substring(0, comma1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            message = line.substring(comma1 + 1, comma2 > comma1 ? comma2 : line.length());

            motds.put(day, message);
        }

        Main.motds = Collections.unmodifiableSortedMap(motds);
    }
}
