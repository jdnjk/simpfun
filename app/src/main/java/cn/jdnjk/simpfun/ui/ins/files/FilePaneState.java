package cn.jdnjk.simpfun.ui.ins.files;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.model.FileSortMode;
import cn.jdnjk.simpfun.utils.FilePathUtils;

class FilePaneState {
    private final List<FileItem> fileList = new ArrayList<>();
    private final Set<String> selectedPaths = new LinkedHashSet<>();
    private final List<String> pendingMovePaths = new ArrayList<>();
    private final List<String> backHistory = new ArrayList<>();
    private final List<String> forwardHistory = new ArrayList<>();
    private final String rootPath;
    private String currentPath = "/";
    private FileSortMode sortMode = FileSortMode.NAME_ASC;
    private boolean selectionMode;
    private boolean pendingMoveContainsDirectory;
    private String pendingMoveLabel;
    private int lastSwipeSelectionIndex = -1;

    FilePaneState(String initialPath) {
        this(initialPath, "/");
    }

    FilePaneState(String initialPath, String rootPath) {
        this.rootPath = FilePathUtils.sanitizePath(rootPath);
        setCurrentPath(initialPath);
    }

    List<FileItem> getFileList() {
        return fileList;
    }

    Set<String> getSelectedPaths() {
        return selectedPaths;
    }

    List<String> copySelectedPaths() {
        return new ArrayList<>(selectedPaths);
    }

    List<FileItem> copySelectedItems() {
        List<FileItem> items = new ArrayList<>();
        for (FileItem item : fileList) {
            if (item.isParentEntry()) {
                continue;
            }
            if (selectedPaths.contains(getItemPath(item))) {
                items.add(item);
            }
        }
        return items;
    }

    List<String> copyPendingMovePaths() {
        return new ArrayList<>(pendingMovePaths);
    }

    String getCurrentPath() {
        return currentPath;
    }

    String getRootPath() {
        return rootPath;
    }

    FileSortMode getSortMode() {
        return sortMode;
    }

    void setSortMode(FileSortMode sortMode) {
        this.sortMode = sortMode != null ? sortMode : FileSortMode.NAME_ASC;
    }

    /**
     * 根据当前排序方式构建比较器。
     * 所有模式下目录始终排在文件前面，同类别内再按所选键排序。
     */
    Comparator<FileItem> buildSortComparator() {
        Comparator<FileItem> keyComparator;
        switch (sortMode) {
            case NAME_ASC:
            case NAME_DESC:
                keyComparator = Comparator.comparing(i -> i.getName().toLowerCase(Locale.ROOT));
                break;
            case SIZE_ASC:
            case SIZE_DESC:
                keyComparator = Comparator.comparingLong(FileItem::getSize);
                break;
            case DATE_ASC:
            case DATE_DESC:
                keyComparator = Comparator.comparing(FileItem::getModifiedAt);
                break;
            case TYPE_ASC:
            case TYPE_DESC:
                keyComparator = Comparator.comparing(FilePaneState::extractExtension);
                break;
            default:
                keyComparator = Comparator.comparing(i -> i.getName().toLowerCase(Locale.ROOT));
                break;
        }
        Comparator<FileItem> comparator = Comparator
                .comparing(FileItem::isFile)
                .thenComparing(keyComparator);
        boolean desc = sortMode == FileSortMode.NAME_DESC
                || sortMode == FileSortMode.SIZE_DESC
                || sortMode == FileSortMode.DATE_DESC
                || sortMode == FileSortMode.TYPE_DESC;
        return desc ? comparator.reversed() : comparator;
    }

    private static String extractExtension(FileItem item) {
        String name = item.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    void setCurrentPath(String currentPath) {
        this.currentPath = clampToRoot(FilePathUtils.sanitizePath(currentPath));
        resetSwipeSelectionAnchor();
    }

    void navigateTo(String path) {
        String target = clampToRoot(FilePathUtils.sanitizePath(path));
        if (target.equals(currentPath)) {
            return;
        }
        backHistory.add(currentPath);
        forwardHistory.clear();
        setCurrentPath(target);
    }


    String getBackHistoryTarget() {
        return backHistory.isEmpty() ? null : backHistory.get(backHistory.size() - 1);
    }

    String getForwardHistoryTarget() {
        return forwardHistory.isEmpty() ? null : forwardHistory.get(forwardHistory.size() - 1);
    }

    boolean goBackInHistory() {
        if (backHistory.isEmpty()) {
            return false;
        }
        forwardHistory.add(currentPath);
        setCurrentPath(backHistory.remove(backHistory.size() - 1));
        return true;
    }

    boolean goForwardInHistory() {
        if (forwardHistory.isEmpty()) {
            return false;
        }
        backHistory.add(currentPath);
        setCurrentPath(forwardHistory.remove(forwardHistory.size() - 1));
        return true;
    }

    boolean isAtRoot() {
        return rootPath.equals(currentPath);
    }

    String getParentPath() {
        if (isAtRoot()) {
            return rootPath;
        }
        return clampToRoot(FilePathUtils.getParentPath(currentPath));
    }

    boolean isSelectionMode() {
        return selectionMode;
    }

    boolean hasSelection() {
        return !selectedPaths.isEmpty();
    }

    boolean hasPendingMove() {
        return !pendingMovePaths.isEmpty();
    }

    String getPendingMoveLabel() {
        return pendingMoveLabel;
    }

    void replaceFileList(List<FileItem> items) {
        fileList.clear();
        fileList.addAll(items);
        fileList.sort(buildSortComparator());
        resetSwipeSelectionAnchor();
    }

    String getItemPath(FileItem item) {
        if (item.isParentEntry()) {
            return currentPath;
        }
        return FilePathUtils.appendPath(currentPath, item.getName());
    }

    List<String> singlePathList(FileItem item) {
        List<String> paths = new ArrayList<>(1);
        paths.add(getItemPath(item));
        return paths;
    }

    void toggleSelection(FileItem item) {
        if (item.isParentEntry()) {
            return;
        }
        String path = getItemPath(item);
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path);
        } else {
            selectedPaths.add(path);
        }
        selectionMode = !selectedPaths.isEmpty();
        if (!selectionMode) {
            resetSwipeSelectionAnchor();
        }
    }

    void clearSelection() {
        selectionMode = false;
        selectedPaths.clear();
        resetSwipeSelectionAnchor();
    }

    void selectBySwipe(int position) {
        if (position < 0 || position >= fileList.size()) {
            return;
        }
        FileItem item = fileList.get(position);
        if (item.isParentEntry()) {
            return;
        }
        if (lastSwipeSelectionIndex >= 0 && lastSwipeSelectionIndex < fileList.size() && lastSwipeSelectionIndex != position) {
            selectRange(lastSwipeSelectionIndex, position);
        } else {
            selectedPaths.add(getItemPath(item));
        }
        lastSwipeSelectionIndex = position;
        selectionMode = !selectedPaths.isEmpty();
    }

    void invertSelection() {
        Set<String> inverted = new LinkedHashSet<>();
        for (FileItem item : fileList) {
            if (item.isParentEntry()) {
                continue;
            }
            String path = getItemPath(item);
            if (!selectedPaths.contains(path)) {
                inverted.add(path);
            }
        }
        selectedPaths.clear();
        selectedPaths.addAll(inverted);
        selectionMode = !selectedPaths.isEmpty();
    }

    boolean hasSelectableItems() {
        for (FileItem item : fileList) {
            if (!item.isParentEntry()) {
                return true;
            }
        }
        return false;
    }

    void prepareMove(FileItem item) {
        pendingMovePaths.clear();
        pendingMovePaths.add(getItemPath(item));
        pendingMoveContainsDirectory = !item.isFile();
        pendingMoveLabel = item.getName();
        clearSelection();
    }

    boolean prepareMoveSelected() {
        if (selectedPaths.isEmpty()) {
            return false;
        }
        pendingMovePaths.clear();
        pendingMovePaths.addAll(selectedPaths);
        pendingMoveContainsDirectory = containsSelectedDirectory();
        pendingMoveLabel = selectedPaths.size() == 1 ? getFileNameFromPath(selectedPaths.iterator().next()) : selectedPaths.size() + " 项";
        clearSelection();
        return true;
    }

    void clearPendingMove() {
        pendingMovePaths.clear();
        pendingMoveContainsDirectory = false;
        pendingMoveLabel = null;
    }

    String validateMoveTarget(String targetPath) {
        String safeTarget = FilePathUtils.sanitizePath(targetPath);
        for (String sourcePath : pendingMovePaths) {
            String parent = FilePathUtils.getParentPath(sourcePath);
            if (parent.equals(safeTarget)) {
                return "目标目录与当前位置相同";
            }
            if (pendingMoveContainsDirectory && (safeTarget.equals(sourcePath) || safeTarget.startsWith(sourcePath + "/"))) {
                return "不能移动目录到自身或子目录";
            }
        }
        return null;
    }

    List<String> toCurrentDirectoryNames(List<String> paths) {
        List<String> names = new ArrayList<>();
        String prefix = "/".equals(currentPath) ? "/" : currentPath + "/";
        for (String path : paths) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            if ("/".equals(currentPath) && path.startsWith("/") && path.length() > 1) {
                names.add(path.substring(1));
            } else if (path.startsWith(prefix) && path.length() > prefix.length()) {
                names.add(path.substring(prefix.length()));
            }
        }
        return names;
    }

    boolean isArchiveFile(FileItem item) {
        String name = item.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".7z");
    }

    private String clampToRoot(String path) {
        if ("/".equals(rootPath)) {
            return path;
        }
        if (rootPath.equals(path) || path.startsWith(rootPath + "/")) {
            return path;
        }
        return rootPath;
    }

    private void selectRange(int first, int second) {
        int start = Math.min(first, second);
        int end = Math.max(first, second);
        for (int i = start; i <= end; i++) {
            FileItem item = fileList.get(i);
            if (!item.isParentEntry()) {
                selectedPaths.add(getItemPath(item));
            }
        }
    }

    private void resetSwipeSelectionAnchor() {
        lastSwipeSelectionIndex = -1;
    }

    private boolean containsSelectedDirectory() {
        for (FileItem item : fileList) {
            if (item.isParentEntry() || item.isFile()) {
                continue;
            }
            if (selectedPaths.contains(getItemPath(item))) {
                return true;
            }
        }
        return false;
    }

    private String getFileNameFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int index = path.lastIndexOf('/');
        return index >= 0 && index < path.length() - 1 ? path.substring(index + 1) : path;
    }
}
