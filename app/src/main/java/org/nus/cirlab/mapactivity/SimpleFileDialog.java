package org.nus.cirlab.mapactivity;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class SimpleFileDialog {

    private String m_sdcardDirectory = "";
    private Context m_context;
    private TextView m_titleView1;
    private TextView m_titleView;
    public String default_file_name = "default.txt";
    private String selected_file_name = default_file_name;
    private EditText input_text;

    private String m_dir = "";
    private List<String> m_subdirs = new ArrayList<>();
    private SimpleFileDialogListener m_SimpleFileDialogListener = null;
    public MyArrayAdapter m_listAdapter = null;
    private boolean m_goToUpper = false;
    public AlertDialog.Builder dialogBuilder = null;

    //////////////////////////////////////////////////////
    // Callback interface for selected directory
    //////////////////////////////////////////////////////
    public interface SimpleFileDialogListener {
        void onChosenDir(String chosenDir);
    }

    public SimpleFileDialog(Context context, SimpleFileDialogListener SimpleFileDialogListener) {

        m_goToUpper = true;
        m_context = context;
        m_sdcardDirectory = m_context.getExternalCacheDir().getAbsolutePath();
        m_SimpleFileDialogListener = SimpleFileDialogListener;

        try {
            m_sdcardDirectory = new File(m_sdcardDirectory).getCanonicalPath();
            m_dir = m_sdcardDirectory + "/PiLoc/";
            Log.v("FileSystem", "dir=" + m_dir);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // chooseFile_or_Dir() - load directory chooser dialog for initial
    // default sdcard directory
    ///////////////////////////////////////////////////////////////////////
    public void chooseFile_or_Dir() {
        chooseFile_or_Dir(m_dir);
    }

    class SimpleFileDialogOnClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int item) {
            String m_dir_old = m_dir;
            String sel = "" + ((AlertDialog) dialog).getListView().getAdapter().getItem(item);
            if (sel.charAt(sel.length() - 1) == '/') sel = sel.substring(0, sel.length() - 1);

            // Navigate into the sub-directory
            if (sel.equals("..")) {
                m_dir = m_dir.substring(0, m_dir.lastIndexOf("/"));
                if ("".equals(m_dir)) {
                    m_dir = "/";
                }
            } else {
                m_dir += "/" + sel;
            }
            selected_file_name = default_file_name;

            if ((new File(m_dir).isFile())) // If the selection is a regular file
            {
                m_dir = m_dir_old;
                selected_file_name = sel;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            updateDirectory();
        }
    }


    ////////////////////////////////////////////////////////////////////////////////
    // chooseFile_or_Dir(String dir) - load directory chooser dialog for initial
    // input 'dir' directory
    ////////////////////////////////////////////////////////////////////////////////
    public void chooseFile_or_Dir(String dir) {
        File dirFile = new File(dir);
        while (!dirFile.exists() || !dirFile.isDirectory()) {
            dir = dirFile.getParent();
            dirFile = new File(dir);
            Log.d("~~~~~", "dir=" + dir);
        }
        Log.d("~~~~~", "dir=" + dir);
        //m_sdcardDirectory
        try {
            dir = new File(dir).getCanonicalPath();
        } catch (IOException ioe) {
            return;
        }

        m_dir = dir;
        m_subdirs = getDirectories(dir);

        dialogBuilder = createDirectoryChooserDialog(dir, m_subdirs,
                new SimpleFileDialogOnClickListener());

        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Current directory chosen
                // Call registered listener supplied with the chosen directory
                if (m_SimpleFileDialogListener != null) {
                    {
                        selected_file_name = input_text.getText() + "";
                        m_SimpleFileDialogListener.onChosenDir(m_dir + "/" + selected_file_name);
                    }
                }
            }
        }).setNegativeButton("Cancel", null);

        final AlertDialog dirsDialog = dialogBuilder.create();

        // Show directory chooser dialog
        dirsDialog.show();
    }


    //get sub-folder list
    private List<String> getDirectories(String dir) {
        List<String> dirs = new ArrayList<>();
        try {
            File dirFile = new File(dir);

            // if directory is not the base sd card directory add ".." for going up one directory
            if ((m_goToUpper || !m_dir.equals(m_sdcardDirectory))
                    && !"/".equals(m_dir)
                    ) {
                dirs.add("..");
            }
            Log.d("~~~~", "m_dir=" + m_dir);
            if (!dirFile.exists() || !dirFile.isDirectory()) {
                return dirs;
            }

            for (File file : dirFile.listFiles()) {
                if (file.isDirectory()) {
                    // Add "/" to directory names to identify them in the list
                    dirs.add(file.getName() + "/");
                } else {
                    // Add file names to the list if we are doing a file save or file open operation
                    dirs.add(file.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(dirs, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });
        return dirs;
    }


    private AlertDialog.Builder createDirectoryChooserDialog(String title, List<String> listItems,
                                                             DialogInterface.OnClickListener onClickListener) {
        dialogBuilder = new AlertDialog.Builder(m_context);
        m_titleView1 = new TextView(m_context);
        m_titleView1.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        m_titleView1.setText("Open:");

        //need to make this a variable Save as, Open, Select Directory
        m_titleView1.setGravity(Gravity.CENTER_VERTICAL);
        m_titleView1.setBackgroundColor(m_context.getResources().getColor(android.R.color.background_dark)); // dark gray 	-12303292
        m_titleView1.setTextColor(m_context.getResources().getColor(android.R.color.white));

        // Create custom view for AlertDialog title
        LinearLayout titleLayout1 = new LinearLayout(m_context);
        titleLayout1.setOrientation(LinearLayout.VERTICAL);
        titleLayout1.addView(m_titleView1);

        LinearLayout titleLayout = new LinearLayout(m_context);
        titleLayout.setOrientation(LinearLayout.VERTICAL);

        m_titleView = new TextView(m_context);
        m_titleView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        m_titleView.setBackgroundColor(m_context.getResources().getColor(android.R.color.background_dark)); // dark gray -12303292
        m_titleView.setTextColor(m_context.getResources().getColor(android.R.color.white));
        m_titleView.setGravity(Gravity.CENTER_VERTICAL);
        m_titleView.setText(title);

        titleLayout.addView(m_titleView);


        input_text = new EditText(m_context);
        input_text.setText(default_file_name);
        titleLayout.addView(input_text);

        //////////////////////////////////////////
        // Set Views and Finish Dialog builder  //
        //////////////////////////////////////////
        dialogBuilder.setView(titleLayout);
        dialogBuilder.setCustomTitle(titleLayout1);
        m_listAdapter = createListAdapter(listItems);
        dialogBuilder.setSingleChoiceItems(m_listAdapter, -1, onClickListener);
        dialogBuilder.setCancelable(false);
        m_listAdapter.notifyDataSetChanged();

        return dialogBuilder;
    }

    private void updateDirectory() {
        m_subdirs.clear();
        m_subdirs.addAll(getDirectories(m_dir));
        ((Activity) m_context).runOnUiThread(new Runnable() {
            public void run() {
                m_listAdapter.notifyDataSetChanged();
            }
        });

        m_titleView.setText(m_dir);
        input_text.setText(selected_file_name);
    }

    private MyArrayAdapter createListAdapter(List<String> items) {
        return new MyArrayAdapter(m_context, android.R.layout.select_dialog_item, android.R.id.text1, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    // Enable list item (directory) text wrapping
                    TextView tv = (TextView) v;
                    tv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                    tv.setEllipsize(null);
                }
                return v;
            }
        };
    }

    public class MyArrayAdapter extends ArrayAdapter<String> {

        private final List<String> items;

        public MyArrayAdapter(final Context _context, final int _resource, int textViewResourceId,final List<String> _items) {
            super(_context, _resource,textViewResourceId, _items);

            this.items = _items;
        }

        // IMPORTANT: either override both getCount and getItem or none as they have to access the same list
        @Override
        public int getCount() {
            return this.items.size();
        }

        ;

        @Override
        public String getItem(final int position) {
            return this.items.get(position%this.items.size());
        }

    }
}