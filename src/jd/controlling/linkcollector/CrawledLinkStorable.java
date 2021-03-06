package jd.controlling.linkcollector;

import java.util.ArrayList;

import jd.controlling.linkcrawler.ArchiveInfoStorable;
import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.LinkCrawler;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLinkStorable;

import org.appwork.storage.Storable;
import org.jdownloader.extensions.extraction.BooleanStatus;

public class CrawledLinkStorable implements Storable {

    private CrawledLink link;
    private String      id  = null;
    private long        UID = -1;

    public String getID() {
        return id;
    }

    public void setID(String id) {
        this.id = id;
    }

    public String getName() {
        return link._getName();
    }

    public void setName(String name) {
        link.setName(name);
    }

    public boolean isEnabled() {
        return link.isEnabled();
    }

    public void setEnabled(boolean enabled) {
        link.setEnabled(enabled);
    }

    @SuppressWarnings("unused")
    private CrawledLinkStorable(/* Storable */) {
        this.link = new CrawledLink((String) null);
    }

    public CrawledLinkStorable(CrawledLink link) {
        this.link = link;
    }

    public void setSourceUrls(String[] urls) {
        if (urls != null) {
            final ArrayList<String> deDuplicatedURLs = new ArrayList<String>();
            for (final String url : urls) {
                final String deDuplicatedURL = DownloadLink.deDuplicateString(LinkCrawler.cleanURL(url));
                if (deDuplicatedURL != null) {
                    deDuplicatedURLs.add(deDuplicatedURL);
                }
            }
            urls = deDuplicatedURLs.toArray(new String[deDuplicatedURLs.size()]);
        }
        link.setSourceUrls(urls);
    }

    public String[] getSourceUrls() {
        return link.getSourceUrls();
    }

    public static class LinkOriginStorable implements Storable {
        public LinkOriginStorable(/* Storable */) {
        }

        public LinkOriginStorable(LinkOriginDetails origin) {
            this.id = origin.getOrigin().name();
            this.details = origin.getDetails();
        }

        private String id;
        private String details;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }
    }

    public LinkOriginStorable getOriginDetails() {
        final LinkOriginDetails origin = link.getOrigin();
        if (origin == null) {
            return null;
        }
        return new LinkOriginStorable(origin);
    }

    public void setOriginDetails(LinkOriginStorable origin) {
        if (origin != null) {
            try {
                final LinkOrigin enu = LinkOrigin.valueOf(origin.id);
                link.setOrigin(LinkOriginDetails.getInstance(enu, origin.details));
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public long getUID() {
        DownloadLink dll = link.getDownloadLink();
        if (dll != null) {
            return dll.getUniqueID().getID();
        }
        return link.getUniqueID().getID();
    }

    public void setUID(long id) {
        this.UID = id;
    }

    public DownloadLinkStorable getDownloadLink() {
        return new DownloadLinkStorable(link.getDownloadLink());
    }

    public void setDownloadLink(DownloadLinkStorable link) {
        this.link.setDownloadLink(link._getDownloadLink());
    }

    /**
     * @param created
     *            the created to set
     */
    public void setCreated(long created) {
        link.setCreated(created);
    }

    /**
     * @return the created
     */
    public long getCreated() {
        return link.getCreated();
    }

    public CrawledLink _getCrawledLink() {
        DownloadLink dll = link.getDownloadLink();
        if (dll != null) {
            if (UID != -1) {
                dll.getUniqueID().setID(UID);
            }
        }
        if (UID != -1) {
            link.getUniqueID().setID(UID);
        }
        return link;
    }

    public ArchiveInfoStorable getArchiveInfo() {
        if (link.hasArchiveInfo()) {
            return new ArchiveInfoStorable(link.getArchiveInfo());
        }
        return null;
    }

    public void setArchiveInfo(ArchiveInfoStorable info) {
        if (info != null) {
            boolean setArchiveInfo = !BooleanStatus.UNSET.equals(info.getAutoExtract());
            if (setArchiveInfo == false) {
                setArchiveInfo = info.getExtractionPasswords() != null && info.getExtractionPasswords().size() > 0;
            }
            if (setArchiveInfo) {
                link.setArchiveInfo(info._getArchiveInfo());
            }
        }
    }

}
