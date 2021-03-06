//jDownloader - Downloadmanager
//Copyright (C) 2012  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.requests.GetRequest;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "servus.com", "pm-wissen.com" }, urls = { "https?://(?:www\\.)?(?:servus|servustv)\\.com/(?:(?:.*/)?videos/|(?:de|at)/p/[^/]+/)([A-Za-z0-9\\-]+)", "https?://(?:www\\.)?(?:pm-wissen)\\.com/(?:(?:.*/)?videos/|(?:de|at)/p/[^/]+/)([A-Za-z0-9\\-]+)" })
public class ServusCom extends PluginForHost {
    public ServusCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.servustv.com/Nutzungsbedingungen";
    }

    @Override
    public String rewriteHost(String host) {
        /* 2020-02-27: servustv.com does still exist and is still used but we've already switched to servus.com a long time ago ... */
        if ("servustv.com".equals(getHost())) {
            if (host == null || "servustv.com".equals(host)) {
                return "servus.com";
            }
        }
        return super.rewriteHost(host);
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String linkid = getFID(link);
        if (linkid != null) {
            return this.getHost() + "://" + linkid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        String fid = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(0);
        if (fid != null) {
            fid = fid.toUpperCase();
        }
        return fid;
    }

    private static Object        LOCK                                     = new Object();
    private static String        authToken                                = null;
    private static long          authLastRefreshedTimestamp               = -1;
    private static final boolean useNewAPI                                = true;
    private Map<String, Object>  entries                                  = null;
    private static final String  PROPERTY_HAS_TRIED_TO_CRAWL_RELEASE_DATE = "HAS_TRIED_TO_CRAWL_RELEASE_DATE";
    private static final String  PROPERTY_DATE_FORMATTED                  = "DATE_FORMATTED";

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformation(link, false);
    }

    private AvailableStatus requestFileInformation(final DownloadLink link, final boolean isDownload) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.setAllowedResponseCodes(new int[] { 409 });
        final String fid = this.getFID(link);
        if (fid == null) {
            /* This should never happen */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        br.setCurrentURL(link.getPluginPatternMatcher());
        String date = null, title = null, episodename = null, labelGroup = null, description = null;
        final String episodenumber = new Regex(link.getPluginPatternMatcher(), "pisode\\-(\\d+)").getMatch(0);
        String dateFormatted = link.getStringProperty(PROPERTY_DATE_FORMATTED, null);
        if (useNewAPI) {
            synchronized (LOCK) {
                final boolean refreshToken;
                if (authToken == null) {
                    logger.info("Token refresh needed because none is available");
                    refreshToken = true;
                } else if (System.currentTimeMillis() - authLastRefreshedTimestamp > 1 * 60 * 60 * 1000l) {
                    logger.info("Token refresh needed because old one is too old");
                    refreshToken = true;
                } else {
                    logger.info("No token refresh needed -> Re-using existing token: " + authToken);
                    refreshToken = false;
                }
                if (refreshToken) {
                    logger.info("Obtaining current authorization value");
                    final Browser brc = br.cloneBrowser();
                    brc.getPage("https://player.redbull.com/1.2.15-stv-release-723/rbup-datamanager.min.js");
                    authToken = brc.getRegex("international/assets/\",a\\.auth=\"([^\"]+)").getMatch(0);
                    if (authToken == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    } else {
                        authLastRefreshedTimestamp = System.currentTimeMillis();
                    }
                }
            }
            PostRequest tokenRequest = br.createPostRequest("https://auth.redbullmediahouse.com/token", "grant_type=client_credentials");
            tokenRequest.getHeaders().put("Authorization", "Basic " + authToken);
            br.getPage(tokenRequest);
            /* 2020-10-19: This one will typically be valid for 5 minutes */
            entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
            final String access_token = (String) entries.get("access_token");
            if (StringUtils.isEmpty(access_token)) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            GetRequest assetRequest = br.createGetRequest("https://sparkle-api.liiift.io/api/v1/stv/channels/international/assets/" + fid.toUpperCase(Locale.ENGLISH));
            assetRequest.getHeaders().put("Authorization", "Bearer " + access_token);
            br.getPage(assetRequest);
            if (br.getHttpConnection().getResponseCode() == 404 || br.getHttpConnection().getResponseCode() == 409) {
                /* 2020-10-20: E.g. {"code":404,"message":"Asset not found"} */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
            final String contentType = (String) entries.get("contentType");
            if (!StringUtils.equalsIgnoreCase(contentType, "video")) {
                /*
                 * 2020-10-20: E.g. "bundle" --> https://www.servustv.com/videos/aa-1q93mgb3w1w11/ --> Overview of series of video but
                 * nothing downloadable.
                 */
                logger.info("Content is not downloadable");
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            final ArrayList<Object> attributes = (ArrayList<Object>) entries.get("attributes");
            /* TODO: This is NOT the release-date! */
            // date = (String) entries.get("lastPublished");
            title = (String) this.getAttribute(attributes, "title");
            episodename = (String) this.getAttribute(attributes, "chapter");
            description = (String) this.getAttribute(attributes, "long_description");
            final String source_list_schedule_data = (String) this.getAttribute(attributes, "source_list_schedule_data");
            if (source_list_schedule_data != null) {
                try {
                    /*
                     * 2020-10-21: WTF this almost always contains multiple items but with the same dates --> Let's just grab the first one
                     */
                    final ArrayList<Object> dateList = JSonStorage.restoreFromString(source_list_schedule_data, TypeRef.LIST);
                    final Map<String, Object> dateInfo = (Map<String, Object>) dateList.get(0);
                    date = (String) dateInfo.get("startTimestamp");
                } catch (final Throwable e) {
                    logger.log(e);
                    logger.info("Failed to grab releasedate via API");
                }
            }
            /* 2020-10-21: Fallback in case release-date is not given via API. */
            if (StringUtils.isEmpty(dateFormatted) && !link.getBooleanProperty(PROPERTY_HAS_TRIED_TO_CRAWL_RELEASE_DATE, false)) {
                try {
                    br.getPage(link.getPluginPatternMatcher());
                    /*
                     * json will only be available for content which is already streamable not e.g. for content which hasn't been released
                     * yet!
                     */
                    final String json = br.getRegex("<script type=\"application/ld\\+json\">([^<]+VideoObject[^<]+)</script>").getMatch(0);
                    if (json != null) {
                        link.setProperty(PROPERTY_HAS_TRIED_TO_CRAWL_RELEASE_DATE, true);
                        final Map<String, Object> websiteData = JSonStorage.restoreFromString(json, TypeRef.HASHMAP);
                        if (StringUtils.isEmpty(title)) {
                            title = (String) websiteData.get("name");
                        }
                        if (StringUtils.isEmpty(description)) {
                            description = (String) websiteData.get("description");
                        }
                        date = (String) websiteData.get("uploadDate");
                    }
                } catch (final Throwable e) {
                    logger.log(e);
                    logger.info("Failed to grab release-date");
                }
            }
        } else {
            /* 2020-10-20: Old way - still works fine to obtain information about items but downloading won't work! */
            br.getPage("https://stv.rbmbtnx.net/api/v1/manifests/" + fid + "/metadata");
            if (br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
            // final ArrayList<Object> ressourcelist = (ArrayList<Object>) entries.get("");
            date = (String) JavaScriptEngineFactory.walkJson(entries, "playability/{0}/{0}/startDate");
            title = (String) entries.get("titleStv");
            episodename = (String) entries.get("chapter");
            labelGroup = (String) entries.get("labelGroup");
        }
        if (StringUtils.isEmpty(labelGroup)) {
            labelGroup = "ServusTV";
        }
        if (StringUtils.isEmpty(title)) {
            /* Fallback */
            title = this.getFID(link);
        }
        title = title.trim();
        if (dateFormatted == null && date != null) {
            dateFormatted = formatDate(date);
            link.setProperty(PROPERTY_DATE_FORMATTED, dateFormatted);
        }
        String filename = "";
        if (dateFormatted != null) {
            filename = dateFormatted + "_";
        }
        filename += labelGroup + "_" + title;
        if (episodenumber != null && !title.contains(episodenumber)) {
            filename += "_" + episodenumber;
        }
        /* Title sometimes already contains episodename --> Do not add it twice! */
        if (episodename != null && !filename.contains(episodename)) {
            filename += " - " + episodename;
        }
        filename = Encoding.htmlDecode(filename);
        filename += ".mp4";
        link.setFinalFileName(filename);
        if (StringUtils.isEmpty(link.getComment()) && !StringUtils.isEmpty(description)) {
            link.setComment(description);
        }
        return AvailableStatus.TRUE;
    }

    /** 2020-10-19: Wrapper for sparkle-api.liiift.io json. Contains ArrayLists with maps containing keys and values. */
    private Object getAttribute(final ArrayList<Object> fields, final String targetKey) {
        HashMap<String, Object> entries = null;
        for (final Object fieldO : fields) {
            entries = (HashMap<String, Object>) fieldO;
            final String fieldKey = (String) entries.get("fieldKey");
            if (fieldKey.equals(targetKey)) {
                return entries.get("fieldValue");
            }
        }
        return null;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link, true);
        String httpstream = null;
        String hlsMaster = null;
        HlsContainer hlsbest = null;
        if (useNewAPI) {
            /* New */
            // if (StringUtils.isEmpty(this.dllink)) {
            // throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Sendung wurde noch nicht ausgestrahlt oder GEO-blocked",
            // 60 * 60 * 1000l);
            // }
            /*
             * Find http stream - skip everything else! HLS = split audio/video which we cannot handle properly yet so we'll have to skip
             * that too!
             */
            final ArrayList<Object> ressourcelist = (ArrayList<Object>) entries.get("resources");
            for (final Object ressourceO : ressourcelist) {
                entries = (Map<String, Object>) ressourceO;
                final String type = (String) entries.get("type");
                final String url = (String) entries.get("url");
                if (StringUtils.isEmpty(url)) {
                    /* Skip invalid items */
                    continue;
                }
                if (url.contains(".mp4")) {
                    logger.info("Found http stream: " + httpstream);
                    if (StringUtils.isEmpty(httpstream)) {
                        httpstream = url;
                    }
                } else if (type.equalsIgnoreCase("hls")) {
                    hlsMaster = url;
                }
            }
            /*
             * 2020-1021: They got a 2nd HLS stream available that can contain multiple language/quality audio streams. Also that one is in
             * #EXT-X-VERSION:6 while the other one is #EXT-X-VERSION:4. See root json/_meta/links/manifest/href. E.g.
             * https://rd-manifests.liiift.io/api/v1/dam/STV/hls/stv-international-<fid>/master.m3u8
             */
            final boolean preferHLS = false;
            if (!StringUtils.isEmpty(hlsMaster) && preferHLS) {
                br.getPage(hlsMaster);
                hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(this.br));
                if (hlsbest == null) {
                    /* No content available --> Probably the user wants to download hasn't aired yet --> Wait and retry later! */
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Sendung wurde noch nicht ausgestrahlt oder GEO-blocked", 60 * 60 * 1000l);
                }
            }
        } else {
            /* Old */
            if (httpstream == null) {
                /* 2017-10-04: Only hls available and it is very easy to create the master URL --> Do not access Brightcove stuff at all! */
                /* Use this to get some more information about the video [in json]: https://www.servus.com/at/p/<videoid>/personalize */
                hlsMaster = getOldHLSMaster(link);
                br.getPage(hlsMaster);
                hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(this.br));
                if (hlsbest == null) {
                    /* No content available --> Probably the user wants to download hasn't aired yet --> Wait and retry later! */
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Sendung wurde noch nicht ausgestrahlt oder GEO-blocked", 60 * 60 * 1000l);
                }
            }
        }
        /* 2020-10-21: Prefer HLS downloads as http may only be available in up to 720p while HLS is available in 1080p or higher. */
        if (hlsbest != null) {
            final String url_hls = hlsbest.getDownloadurl();
            checkFFmpeg(link, "Download a HLS Stream");
            dl = new HLSDownloader(link, br, url_hls);
            dl.startDownload();
        } else {
            if (httpstream == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            /* Use http as fallback. */
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, httpstream, true, 0);
            if (!this.looksLikeDownloadableContent(dl.getConnection())) {
                try {
                    br.followConnection(true);
                } catch (final IOException e) {
                    logger.log(e);
                }
                if (dl.getConnection().getResponseCode() == 403) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
                } else if (dl.getConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
            dl.startDownload();
        }
    }

    private String getOldHLSMaster(final DownloadLink link) {
        return String.format("https://stv.rbmbtnx.net/api/v1/manifests/%s.m3u8", this.getFID(link));
    }

    @SuppressWarnings({ "static-access" })
    private String formatDate(String input) {
        if (input == null) {
            return null;
        }
        String formattedDate = null;
        long date = 0;
        if (input.matches("\\d+")) {
            date = Long.parseLong(input) * 1000;
        } else if (input.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
            date = TimeFormatter.getMilliSeconds(input, "dd.MM.yyyy", Locale.GERMAN);
        } else if (input.matches("\\d{1,2}\\. [A-Za-z]+ \\d{4} \\| \\d{1,2}:\\d{1,2}")) {
            date = TimeFormatter.getMilliSeconds(input, "dd. MMMM yyyy '|' HH:mm", Locale.GERMAN);
        } else if (input.matches("\\d{4}-\\d{2}-\\d{2}.+")) {
            /* New 2019-12-04 */
            formattedDate = new Regex(input, "^(\\d{4}-\\d{2}-\\d{2})").getMatch(0);
        } else {
            final Calendar cal = Calendar.getInstance();
            input += cal.get(cal.YEAR);
            date = TimeFormatter.getMilliSeconds(input, "E '|' dd.MM.yyyy", Locale.GERMAN);
        }
        if (formattedDate == null) {
            final String targetFormat = "yyyy-MM-dd";
            Date theDate = new Date(date);
            try {
                final SimpleDateFormat formatter = new SimpleDateFormat(targetFormat);
                formattedDate = formatter.format(theDate);
            } catch (Exception e) {
                /* prevent input error killing plugin */
                formattedDate = input;
            }
        }
        return formattedDate;
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }
}