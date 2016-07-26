package net.maxsmr.mediaplayercontroller.playlist;

public class PlaylistItem {

    /** may be full uri or file path  */
    public final String track;

    public PlaylistItem(String track) {

//        if (StringUtils.isEmpty(track)) {
//            throw new IllegalArgumentException("empty track: " + track);
//        }

        this.track = track;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlaylistItem that = (PlaylistItem) o;

        return track != null ? track.equals(that.track) : that.track == null;

    }

    @Override
    public int hashCode() {
        return track != null ? track.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "PlaylistItem{" +
                "track='" + track + '\'' +
                '}';
    }
}
