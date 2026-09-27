package cn.jdnjk.simpfun.model;

import android.os.Parcel;
import android.os.Parcelable;

public class AnnouncementItem implements Parcelable {
    private final int id;
    private final String title;
    private final String text;
    private final String createTime;
    private final int readTimes;

    public AnnouncementItem(int id, String title, String text, String createTime, int readTimes) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.createTime = createTime;
        this.readTimes = readTimes;
    }

    protected AnnouncementItem(Parcel in) {
        id = in.readInt();
        title = in.readString();
        text = in.readString();
        createTime = in.readString();
        readTimes = in.readInt();
    }

    public static final Creator<AnnouncementItem> CREATOR = new Creator<AnnouncementItem>() {
        @Override
        public AnnouncementItem createFromParcel(Parcel in) {
            return new AnnouncementItem(in);
        }

        @Override
        public AnnouncementItem[] newArray(int size) {
            return new AnnouncementItem[size];
        }
    };

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getText() { return text; }
    public String getCreateTime() { return createTime; }
    public int getReadTimes() { return readTimes; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(title);
        dest.writeString(text);
        dest.writeString(createTime);
        dest.writeInt(readTimes);
    }
}
