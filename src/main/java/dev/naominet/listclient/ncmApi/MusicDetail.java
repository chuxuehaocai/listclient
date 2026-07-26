package dev.naominet.listclient.ncmApi;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MusicDetail {
    public int code;
    public List<Song> songs;
    public List<Privilege> privileges;

    /* ---- Song ---- */

    public static class Song {
        public long id;
        public String name;
        public String mainTitle;
        public String additionalTitle;
        public int pst;
        public int t;
        public List<Artist> ar;
        public List<String> alia;
        public int pop;
        public int st;
        public String rt;
        public int fee;
        public int v;
        public String cf;
        public Album al;
        /** 时长 (ms) */
        public int dt;
        public Quality h;
        public Quality m;
        public Quality l;
        public Quality sq;
        public Quality hr;
        public String cd;
        public int no;
        public int ftype;
        public int djId;
        public int copyright;
        public int mv;
        public int mst;
        public long cp;
        public int rtype;
        public String rurl;
        public long publishTime;
        public List<String> tns;
        public int single;
        @SerializedName("mark")
        public long markBits;
        public boolean resourceState;
        public int version;
        public int originCoverType;
        public int s_id;
    }

    /* ---- Album ---- */

    public static class Album {
        public long id;
        public String name;
        public String picUrl;
        public List<String> tns;
        @SerializedName("pic_str")
        public String picStr;
        public long pic;
    }

    /* ---- Artist ---- */

    public static class Artist {
        public long id;
        public String name;
        public List<String> tns;
        public List<String> alias;
    }

    /* ---- Quality ---- */

    public static class Quality {
        public int br;
        public long fid;
        public long size;
        public int vd;
        public int sr;
    }

    /* ---- Privilege ---- */

    public static class Privilege {
        public long id;
        public int fee;
        public int payed;
        public int st;
        public int pl;
        public int dl;
        public int sp;
        public int cp;
        public int subp;
        public boolean cs;
        public int maxbr;
        public int flag;
        public boolean preSell;
        public int playMaxbr;
        public int downloadMaxbr;
        public String maxBrLevel;
        public String playMaxBrLevel;
        public String downloadMaxBrLevel;
        public String plLevel;
        public String dlLevel;
        public String flLevel;
        public int rightSource;
        public int code;
        public String message;
        public FreeTrialPrivilege freeTrialPrivilege;
        public List<ChargeInfo> chargeInfoList;
    }

    /* ---- FreeTrialPrivilege ---- */

    public static class FreeTrialPrivilege {
        public boolean resConsumable;
        public boolean userConsumable;
        public String listenType;
        public Integer cannotListenReason;
    }

    /* ---- ChargeInfo ---- */

    public static class ChargeInfo {
        public int rate;
        public String chargeUrl;
        public String chargeMessage;
        public int chargeType;
    }
}
