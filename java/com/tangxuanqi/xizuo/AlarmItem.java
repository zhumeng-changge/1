package com.tangxuanqi.xizuo;

import android.os.Parcel;
import android.os.Parcelable;

/** 一条提醒（网页端算好后交给原生侧做系统级闹钟） */
public class AlarmItem implements Parcelable {
    public String id = "";
    public long at = 0;          // 触发时间（毫秒）
    public String title = "";
    public String desc = "";
    public int kind = 0;         // 0 通用 1 上课 2 Python 3 作息

    public AlarmItem() {}

    protected AlarmItem(Parcel in) {
        id = in.readString();
        at = in.readLong();
        title = in.readString();
        desc = in.readString();
        kind = in.readInt();
    }

    /** 用具名嵌套类代替匿名类：d8/R8 在处理 JDK 编译出的匿名类时容易出问题 */
    public static final class CreatorImpl implements Parcelable.Creator<AlarmItem> {
        @Override public AlarmItem createFromParcel(Parcel in) { return new AlarmItem(in); }
        @Override public AlarmItem[] newArray(int size) { return new AlarmItem[size]; }
    }

    public static final Creator<AlarmItem> CREATOR = new CreatorImpl();

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeLong(at);
        dest.writeString(title);
        dest.writeString(desc);
        dest.writeInt(kind);
    }

    /** 取整到分钟，方便判重 */
    public long atMinute() { return at / 60000L; }

    public String key() { return id + "@" + atMinute(); }
}
