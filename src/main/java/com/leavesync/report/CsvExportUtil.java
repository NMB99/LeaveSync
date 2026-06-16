package com.leavesync.report;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CsvExportUtil {

    private CsvExportUtil() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static <T extends Record> String exportToCsv(List<T> data) {

        if (data.isEmpty()) {
            return "";
        }

        RecordComponent[] components = data.get(0).getClass().getRecordComponents();

        String header = Arrays.stream(components)
                .map(RecordComponent::getName)
                .collect(Collectors.joining(","));

        String rows = data.stream()
                .map(item -> buildRow(item, components))
                .collect(Collectors.joining("\n"));

        return header + "\n" + rows;
    }

    public static String exportAbsencePatternsToCsv(List<AbsencePatternResponse> data) {

        if (data.isEmpty()) {
            return "";
        }

        String header = "userId,employeeName,instanceCount,leaveRequestId,startDate,endDate,totalWorkingDays,status";

        String rows = data.stream()
                .flatMap(pattern -> pattern.instances().stream()
                        .map(instance -> String.join(",",
                                pattern.userId().toString(),
                                pattern.employeeName(),
                                String.valueOf(pattern.instanceCount()),
                                instance.leaveRequestId().toString(),
                                instance.startDate().toString(),
                                instance.endDate().toString(),
                                instance.totalWorkingDays().toString(),
                                instance.status().toString()
                        ))
                )
                .collect(Collectors.joining("\n"));

        return header + "\n" + rows;
    }

    private static String buildRow(Object item, RecordComponent[] components) {
        return Arrays.stream(components)
                .map(component -> {
                    try {
                        Object value = component.getAccessor().invoke(item);
                        return value != null ? value.toString() : "";
                    }
                    catch (Exception e) {
                        return "";
                    }
                })
                .collect(Collectors.joining(","));
    }
}
