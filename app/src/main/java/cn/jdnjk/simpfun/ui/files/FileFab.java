package cn.jdnjk.simpfun.ui.files;

import android.view.View;
import android.widget.TextView;
import cn.jdnjk.simpfun.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class FileFab {
    private FloatingActionButton fabMain;
    private FloatingActionButton fabNewFolder;
    private FloatingActionButton fabNewFile;
    private FloatingActionButton fabUploadFile;
    private FloatingActionButton fabToolbox;

    private FloatingActionButton fabCompress;
    private FloatingActionButton fabExtract;
    private FloatingActionButton fabRename;
    private FloatingActionButton fabDelete;
    private FloatingActionButton fabCut;
    private FloatingActionButton fabCopy;
    private FloatingActionButton fabPaste;

    // 文本标签
    private TextView tvNewFolder;
    private TextView tvNewFile;
    private TextView tvUploadFile;
    private TextView tvToolbox;
    private TextView tvCompress;
    private TextView tvExtract;
    private TextView tvRename;
    private TextView tvDelete;
    private TextView tvCut;
    private TextView tvCopy;
    private TextView tvPaste;
    
    private FileListFragment fragment;
    private FileCopy fileCopy;
    private FileDelete fileDelete;
    private FileExtract fileExtract;

    public void initFabButtons(FileListFragment fragment, View view) {
        this.fragment = fragment;
        
        // 初始化辅助类
        this.fileCopy = new FileCopy(fragment);
        this.fileDelete = new FileDelete(fragment);
        this.fileExtract = new FileExtract(fragment);
        
        fabMain = view.findViewById(R.id.fab_main);
        fabNewFolder = view.findViewById(R.id.fab_new_folder);
        fabNewFile = view.findViewById(R.id.fab_new_file);
        fabUploadFile = view.findViewById(R.id.fab_upload_file);
        fabToolbox = view.findViewById(R.id.fab_toolbox);

        fabCompress = view.findViewById(R.id.fab_compress);
        fabExtract = view.findViewById(R.id.fab_extract);
        fabRename = view.findViewById(R.id.fab_rename);
        fabDelete = view.findViewById(R.id.fab_delete);
        fabCut = view.findViewById(R.id.fab_cut);
        fabCopy = view.findViewById(R.id.fab_copy);
        fabPaste = view.findViewById(R.id.fab_paste);

        tvNewFolder = view.findViewById(R.id.tv_new_folder);
        tvNewFile = view.findViewById(R.id.tv_new_file);
        tvUploadFile = view.findViewById(R.id.tv_upload_file);
        tvToolbox = view.findViewById(R.id.tv_toolbox);
        tvCompress = view.findViewById(R.id.tv_compress);
        tvExtract = view.findViewById(R.id.tv_extract);
        tvRename = view.findViewById(R.id.tv_rename);
        tvDelete = view.findViewById(R.id.tv_delete);
        tvCut = view.findViewById(R.id.tv_cut);
        tvCopy = view.findViewById(R.id.tv_copy);
        tvPaste = view.findViewById(R.id.tv_paste);

        fabMain.setOnClickListener(v -> toggleFabMenu());

        fabNewFolder.setOnClickListener(v -> {
            closeFabMenu();
            fragment.showCreateFolderDialog();
        });

        fabNewFile.setOnClickListener(v -> {
            closeFabMenu();
            fragment.showCreateFileDialog();
        });

        fabUploadFile.setOnClickListener(v -> {
            closeFabMenu();
            fragment.openFileChooser();
        });

        fabToolbox.setOnClickListener(v -> {
            closeFabMenu();
            fragment.showToolboxDialog();
        });

        // 添加选择模式相关的点击监听器
        fabCompress.setOnClickListener(v -> {
            closeFabMenu();
            fileExtract.compressFiles(fragment.getSelectedItems());
        });

        fabExtract.setOnClickListener(v -> {
            closeFabMenu();
            List<FileListFragment.FileItem> selectedItems = fragment.getSelectedItems();
            if (!selectedItems.isEmpty()) {
                fileExtract.extractFile(selectedItems.get(0));
            }
        });

        fabRename.setOnClickListener(v -> {
            closeFabMenu();
            List<FileListFragment.FileItem> selectedItems = fragment.getSelectedItems();
            if (!selectedItems.isEmpty()) {
                fragment.renameFile(selectedItems.get(0));
            }
        });

        fabDelete.setOnClickListener(v -> {
            closeFabMenu();
            fileDelete.deleteFiles(fragment.getSelectedItems());
        });

        fabCut.setOnClickListener(v -> {
            closeFabMenu();
            fileCopy.cutFiles(fragment.getSelectedItems());
        });

        fabCopy.setOnClickListener(v -> {
            closeFabMenu();
            List<FileListFragment.FileItem> selectedItems = fragment.getSelectedItems();
            if (!selectedItems.isEmpty()) {
                fileCopy.copyFile(selectedItems.get(0));
            }
        });

        fabPaste.setOnClickListener(v -> {
            closeFabMenu();
            fileCopy.pasteFiles();
        });
    }

    private void toggleFabMenu() {
        if (fragment.isFabMenuOpen) {
            closeFabMenu();
        } else {
            openFabMenu();
        }
    }

    private void openFabMenu() {
        fragment.isFabMenuOpen = true;

        int selectedCount = fragment.getSelectedItemsCount();

        if (selectedCount == 0) {
            fabNewFolder.setVisibility(View.VISIBLE);
            fabNewFile.setVisibility(View.VISIBLE);
            fabUploadFile.setVisibility(View.VISIBLE);
            fabToolbox.setVisibility(View.VISIBLE);
            tvNewFolder.setVisibility(View.VISIBLE);
            tvNewFile.setVisibility(View.VISIBLE);
            tvUploadFile.setVisibility(View.VISIBLE);
            tvToolbox.setVisibility(View.VISIBLE);

            // 如果有剪贴文件，显示粘贴按钮
            if (!fragment.cutFilesPaths.isEmpty()) {
                fabPaste.setVisibility(View.VISIBLE);
                tvPaste.setVisibility(View.VISIBLE);
            }

            long delay = 100;
            animateButton(fabNewFolder, true, delay);
            animateTextLabel(tvNewFolder, true, delay);
            delay += 56;

            animateButton(fabNewFile, true, delay);
            animateTextLabel(tvNewFile, true, delay);
            delay += 56;

            animateButton(fabUploadFile, true, delay);
            animateTextLabel(tvUploadFile, true, delay);
            delay += 56;

            if (!fragment.cutFilesPaths.isEmpty()) {
                animateButton(fabPaste, true, delay);
                animateTextLabel(tvPaste, true, delay);
                delay += 56;
            }

            animateButton(fabToolbox, true, delay);
            animateTextLabel(tvToolbox, true, delay);

        } else if (selectedCount == 1) {
            List<FileListFragment.FileItem> selectedItems = fragment.getSelectedItems();
            if (!selectedItems.isEmpty()) {
                FileListFragment.FileItem selectedItem = selectedItems.get(0);
                fabCopy.setVisibility(View.VISIBLE);
                fabCompress.setVisibility(View.VISIBLE);
                fabRename.setVisibility(View.VISIBLE);
                fabDelete.setVisibility(View.VISIBLE);
                fabCut.setVisibility(View.VISIBLE);
                tvCopy.setVisibility(View.VISIBLE);
                tvCompress.setVisibility(View.VISIBLE);
                tvRename.setVisibility(View.VISIBLE);
                tvDelete.setVisibility(View.VISIBLE);
                tvCut.setVisibility(View.VISIBLE);

                long delay = 100;
                animateButton(fabCopy, true, delay);
                animateTextLabel(tvCopy, true, delay);
                delay += 56;

                animateButton(fabCompress, true, delay);
                animateTextLabel(tvCompress, true, delay);
                delay += 56;

                if (selectedItem.isExtractable()) {
                    fabExtract.setVisibility(View.VISIBLE);
                    tvExtract.setVisibility(View.VISIBLE);
                    animateButton(fabExtract, true, delay);
                    animateTextLabel(tvExtract, true, delay);
                    delay += 56;
                }

                animateButton(fabRename, true, delay);
                animateTextLabel(tvRename, true, delay);
                delay += 56;

                animateButton(fabDelete, true, delay);
                animateTextLabel(tvDelete, true, delay);
                delay += 56;

                animateButton(fabCut, true, delay);
                animateTextLabel(tvCut, true, delay);
            }
        } else {
            fabCopy.setVisibility(View.VISIBLE);
            fabCompress.setVisibility(View.VISIBLE);
            fabDelete.setVisibility(View.VISIBLE);
            fabCut.setVisibility(View.VISIBLE);
            tvCopy.setVisibility(View.VISIBLE);
            tvCompress.setVisibility(View.VISIBLE);
            tvDelete.setVisibility(View.VISIBLE);
            tvCut.setVisibility(View.VISIBLE);

            animateButton(fabCopy, true, 100);
            animateButton(fabCompress, true, 156);
            animateButton(fabDelete, true, 212);
            animateButton(fabCut, true, 268);
            animateTextLabel(tvCopy, true, 100);
            animateTextLabel(tvCompress, true, 156);
            animateTextLabel(tvDelete, true, 212);
            animateTextLabel(tvCut, true, 268);
        }

        fabMain.animate().rotation(45f).setDuration(300).start();
    }

    private void closeFabMenu() {
        fragment.isFabMenuOpen = false;

        List<FloatingActionButton> allFabs = List.of(
                fabNewFolder, fabNewFile, fabUploadFile, fabToolbox,
                fabCompress, fabExtract, fabRename, fabDelete, fabCut, fabCopy, fabPaste
        );

        List<TextView> allLabels = List.of(
                tvNewFolder, tvNewFile, tvUploadFile, tvToolbox,
                tvCompress, tvExtract, tvRename, tvDelete, tvCut, tvCopy, tvPaste
        );

        long delay = 0;
        for (FloatingActionButton fab : allFabs) {
            if (fab.getVisibility() == View.VISIBLE) {
                animateButton(fab, false, delay);
                delay += 25;
            }
        }

        delay = 0;
        for (TextView label : allLabels) {
            if (label.getVisibility() == View.VISIBLE) {
                animateTextLabel(label, false, delay);
                delay += 25;
            }
        }

        fabMain.animate().rotation(0f).setDuration(300).start();
    }

    private void animateButton(FloatingActionButton button, boolean show, long delay) {
        if (show) {
            button.setScaleX(0f);
            button.setScaleY(0f);
            button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setStartDelay(delay)
                    .start();
        } else {
            button.animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .setDuration(200)
                    .setStartDelay(delay)
                    .withEndAction(() -> button.setVisibility(View.GONE))
                    .start();
        }
    }

    private void animateTextLabel(TextView textView, boolean show, long delay) {
        if (show) {
            textView.setAlpha(0f);
            textView.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setStartDelay(delay)
                    .start();
        } else {
            textView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .setStartDelay(delay)
                    .withEndAction(() -> textView.setVisibility(View.GONE))
                    .start();
        }
    }
    
    public void updateFabMenu() {
        int selectedCount = fragment.getSelectedItemsCount();
        boolean isSelectionMode = selectedCount > 0;

        if (selectedCount == 0) {
            setupDefaultFab();
        } else if (selectedCount == 1) {
            setupSingleSelectionFab();
        } else {
            setupMultiSelectionFab();
        }

        if (fragment.isFabMenuOpen) {
            showCurrentModeButtons();
        }
    }

    private void setupDefaultFab() {
        hideSelectionFabs();

        fabNewFolder.setOnClickListener(v -> {
            closeFabMenu();
            fragment.showCreateFolderDialog();
        });

        fabNewFile.setOnClickListener(v -> {
            closeFabMenu();
            fragment.showCreateFileDialog();
        });

        fabUploadFile.setOnClickListener(v -> {
            closeFabMenu();
            fragment.openFileChooser();
        });

        fabPaste.setOnClickListener(v -> {
            closeFabMenu();
            fileCopy.pasteFiles();
        });

        fabToolbox.setOnClickListener(v -> {
            closeFabMenu();
            fragment.showToolboxDialog();
        });
    }

    private void setupSingleSelectionFab() {
        List<FileListFragment.FileItem> selectedItems = fragment.getSelectedItems();
        if (selectedItems.isEmpty()) return;

        FileListFragment.FileItem selectedItem = selectedItems.get(0);

        hideDefaultFabs();

        fabCompress.setOnClickListener(v -> {
            closeFabMenu();
            fileExtract.compressFiles(selectedItems);
        });

        if (selectedItem.isExtractable()) {
            fabExtract.setVisibility(View.VISIBLE);
            tvExtract.setVisibility(View.VISIBLE);
            fabExtract.setOnClickListener(v -> {
                closeFabMenu();
                fileExtract.extractFile(selectedItem);
            });
        }

        fabRename.setOnClickListener(v -> {
            closeFabMenu();
            fragment.renameFile(selectedItem);
        });

        fabDelete.setOnClickListener(v -> {
            closeFabMenu();
            fileDelete.deleteFiles(selectedItems);
        });

        fabCut.setOnClickListener(v -> {
            closeFabMenu();
            fileCopy.cutFiles(selectedItems);
        });

        if (selectedItem.isFile()) {
            fabCopy.setVisibility(View.VISIBLE);
            tvCopy.setVisibility(View.VISIBLE);
            fabCopy.setOnClickListener(v -> {
                closeFabMenu();
                fileCopy.copyFile(selectedItem);
            });
        }

        fabToolbox.setOnClickListener(v -> {
            closeFabMenu();
            fragment.showToolboxDialog();
        });
    }

    private void setupMultiSelectionFab() {
        List<FileListFragment.FileItem> selectedItems = fragment.getSelectedItems();

        hideDefaultFabs();
        fabExtract.setVisibility(View.GONE);
        tvExtract.setVisibility(View.GONE);
        fabRename.setVisibility(View.GONE);
        tvRename.setVisibility(View.GONE);
        fabCopy.setVisibility(View.GONE);
        tvCopy.setVisibility(View.GONE);

        fabCompress.setOnClickListener(v -> {
            closeFabMenu();
            fileExtract.compressFiles(selectedItems);
        });

        fabDelete.setOnClickListener(v -> {
            closeFabMenu();
            fileDelete.deleteFiles(selectedItems);
        });

        fabCut.setOnClickListener(v -> {
            closeFabMenu();
            fileCopy.cutFiles(selectedItems);
        });

        fabToolbox.setOnClickListener(v -> {
            closeFabMenu();
            fragment.showToolboxDialog();
        });
    }

    private void hideDefaultFabs() {
        fabNewFolder.setVisibility(View.GONE);
        fabNewFile.setVisibility(View.GONE);
        fabUploadFile.setVisibility(View.GONE);
        tvNewFolder.setVisibility(View.GONE);
        tvNewFile.setVisibility(View.GONE);
        tvUploadFile.setVisibility(View.GONE);
    }

    private void hideSelectionFabs() {
        fabCompress.setVisibility(View.GONE);
        fabExtract.setVisibility(View.GONE);
        fabRename.setVisibility(View.GONE);
        fabDelete.setVisibility(View.GONE);
        fabCut.setVisibility(View.GONE);
        fabCopy.setVisibility(View.GONE);
        fabPaste.setVisibility(View.GONE);
        tvCompress.setVisibility(View.GONE);
        tvExtract.setVisibility(View.GONE);
        tvRename.setVisibility(View.GONE);
        tvDelete.setVisibility(View.GONE);
        tvCut.setVisibility(View.GONE);
        tvCopy.setVisibility(View.GONE);
        tvPaste.setVisibility(View.GONE);
    }

    private void showCurrentModeButtons() {
        hideAllButtons();
        int selectedCount = fragment.getSelectedItemsCount();

        if (selectedCount == 0) {
            fabNewFolder.setVisibility(View.VISIBLE);
            fabNewFile.setVisibility(View.VISIBLE);
            fabUploadFile.setVisibility(View.VISIBLE);
            fabToolbox.setVisibility(View.VISIBLE);
            tvNewFolder.setVisibility(View.VISIBLE);
            tvNewFile.setVisibility(View.VISIBLE);
            tvUploadFile.setVisibility(View.VISIBLE);
            tvToolbox.setVisibility(View.VISIBLE);

            if (!fragment.cutFilesPaths.isEmpty()) {
                fabPaste.setVisibility(View.VISIBLE);
                tvPaste.setVisibility(View.VISIBLE);
                fabPaste.setScaleX(1f);
                fabPaste.setScaleY(1f);
                tvPaste.setAlpha(1f);
            }

            fabNewFolder.setScaleX(1f);
            fabNewFolder.setScaleY(1f);
            fabNewFile.setScaleX(1f);
            fabNewFile.setScaleY(1f);
            fabUploadFile.setScaleX(1f);
            fabUploadFile.setScaleY(1f);
            fabToolbox.setScaleX(1f);
            fabToolbox.setScaleY(1f);

            tvNewFolder.setAlpha(1f);
            tvNewFile.setAlpha(1f);
            tvUploadFile.setAlpha(1f);
            tvToolbox.setAlpha(1f);

        } else if (selectedCount == 1) {
            List<FileListFragment.FileItem> selectedItems = fragment.getSelectedItems();
            if (!selectedItems.isEmpty()) {
                FileListFragment.FileItem selectedItem = selectedItems.get(0);

                fabCompress.setVisibility(View.VISIBLE);
                fabRename.setVisibility(View.VISIBLE);
                fabDelete.setVisibility(View.VISIBLE);
                fabCut.setVisibility(View.VISIBLE);
                fabToolbox.setVisibility(View.VISIBLE);
                tvCompress.setVisibility(View.VISIBLE);
                tvRename.setVisibility(View.VISIBLE);
                tvDelete.setVisibility(View.VISIBLE);
                tvCut.setVisibility(View.VISIBLE);
                tvToolbox.setVisibility(View.VISIBLE);

                fabCompress.setScaleX(1f);
                fabCompress.setScaleY(1f);
                fabRename.setScaleX(1f);
                fabRename.setScaleY(1f);
                fabDelete.setScaleX(1f);
                fabDelete.setScaleY(1f);
                fabCut.setScaleX(1f);
                fabCut.setScaleY(1f);
                fabToolbox.setScaleX(1f);
                fabToolbox.setScaleY(1f);

                tvCompress.setAlpha(1f);
                tvRename.setAlpha(1f);
                tvDelete.setAlpha(1f);
                tvCut.setAlpha(1f);
                tvToolbox.setAlpha(1f);

                if (selectedItem.isExtractable()) {                    
                    fabExtract.setVisibility(View.VISIBLE);
                    tvExtract.setVisibility(View.VISIBLE);
                    fabExtract.setScaleX(1f);
                    fabExtract.setScaleY(1f);
                    tvExtract.setAlpha(1f);
                }

                if (selectedItem.isFile()) {
                    fabCopy.setVisibility(View.VISIBLE);
                    tvCopy.setVisibility(View.VISIBLE);
                    fabCopy.setScaleX(1f);
                    fabCopy.setScaleY(1f);
                    tvCopy.setAlpha(1f);
                }
            }
        } else {
            fabCompress.setVisibility(View.VISIBLE);
            fabDelete.setVisibility(View.VISIBLE);
            fabCut.setVisibility(View.VISIBLE);
            fabToolbox.setVisibility(View.VISIBLE);
            tvCompress.setVisibility(View.VISIBLE);
            tvDelete.setVisibility(View.VISIBLE);
            tvCut.setVisibility(View.VISIBLE);
            tvToolbox.setVisibility(View.VISIBLE);

            fabCompress.setScaleX(1f);
            fabCompress.setScaleY(1f);
            fabDelete.setScaleX(1f);
            fabDelete.setScaleY(1f);
            fabCut.setScaleX(1f);
            fabCut.setScaleY(1f);
            fabToolbox.setScaleX(1f);
            fabToolbox.setScaleY(1f);

            tvCompress.setAlpha(1f);
            tvDelete.setAlpha(1f);
            tvCut.setAlpha(1f);
            tvToolbox.setAlpha(1f);
        }
    }

    private void hideAllButtons() {
        List<FloatingActionButton> allFabs = List.of(
                fabNewFolder, fabNewFile, fabUploadFile, fabToolbox,
                fabCompress, fabExtract, fabRename, fabDelete, fabCut, fabCopy, fabPaste
        );

        List<TextView> allLabels = List.of(
                tvNewFolder, tvNewFile, tvUploadFile, tvToolbox,
                tvCompress, tvExtract, tvRename, tvDelete, tvCut, tvCopy, tvPaste
        );

        for (FloatingActionButton fab : allFabs) {
            fab.setVisibility(View.GONE);
        }

        for (TextView label : allLabels) {
            label.setVisibility(View.GONE);
        }
    }
}