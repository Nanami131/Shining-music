package org.L2.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The existing list response remains unchanged for callers that omit pagination parameters. */
public final class ListPagination {
    private ListPagination() {}

    public static R fromPage(R result, long total, int page, int size) {
        if (!Boolean.TRUE.equals(result.getPassed()) || !(result.getData() instanceof List<?>)) return result;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result.getData());
        data.put("page", page);
        data.put("size", size);
        data.put("total", total);
        long pages = (total + size - 1L) / size;
        data.put("totalPages", pages);
        data.put("hasNext", page < pages);
        return result.setData(data);
    }

    public static R apply(R result, Integer page, Integer size) {
        if (page == null && size == null) return result;
        if (page == null || size == null || page < 1 || size < 0 || (size == 0 && page != 1)) {
            return R.error("page 必须大于等于 1，size 为 0 时 page 必须为 1");
        }
        if (!Boolean.TRUE.equals(result.getPassed()) || !(result.getData() instanceof List<?>)) return result;
        List<?> all = (List<?>) result.getData();
        int total = all.size();
        long start = size == 0 ? 0L : ((long) page - 1L) * size;
        int from = (int) Math.min(total, start);
        int to = size == 0 ? total : (int) Math.min(total, start + size);
        int totalPages = size == 0 ? (total == 0 ? 0 : 1) : (int) ((total + (long) size - 1L) / size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", all.subList(from, to));
        data.put("page", page);
        data.put("size", size);
        data.put("total", total);
        data.put("totalPages", totalPages);
        data.put("hasNext", size > 0 && page < totalPages);
        return result.setData(data);
    }
}
