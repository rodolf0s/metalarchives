package de.loki.metallum.core.parser.site;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;
import org.jsoup.Jsoup;

import de.loki.metallum.core.parser.site.helper.LinkParser;
import de.loki.metallum.core.parser.site.helper.label.CurrentRosterParser;
import de.loki.metallum.core.parser.site.helper.label.PastRosterParser;
import de.loki.metallum.core.parser.site.helper.label.ReleaseParser;
import de.loki.metallum.core.util.MetallumUtil;
import de.loki.metallum.core.util.net.MetallumURL;
import de.loki.metallum.core.util.net.downloader.Downloader;
import de.loki.metallum.entity.Band;
import de.loki.metallum.entity.Disc;
import de.loki.metallum.entity.Label;
import de.loki.metallum.entity.Link;
import de.loki.metallum.enums.Country;
import de.loki.metallum.enums.LabelStatus;

public class LabelSiteParser extends AbstractSiteParser<Label> {

	private static Logger	logger	= Logger.getLogger(LabelSiteParser.class);

	/**
	 * There are ways to sort the return values of the rooster and release parsers.<br>
	 * This Enumeration gives you an Idea how you can Sort that data.
	 * 
	 * @author Zarathustra
	 * 
	 */
	public enum PARSE_STYLE {
		/**
		 * If there is nothing to parse.
		 */
		NONE(-1),
		/**
		 * If you want to sort the result by band alphabetical. (suggested)
		 */
		BAND_SEARCH_MODE(0),
		/**
		 * If you want to sort the result by Genre alphabetical.
		 */
		GENRE_SEARCH_MODE(1),
		/**
		 * If you want to sort the result by Country alphabetical.
		 */
		COUNTRY_SEARCH_MODE(2);

		private final int	searchNumber;

		private PARSE_STYLE(final int searchNumber) {
			this.searchNumber = searchNumber;
		}

		public int asSearchNumber() {
			return this.searchNumber;
		}
	}

	private final PARSE_STYLE	loadCurrentRooster;
	private final PARSE_STYLE	loadPastRooster;
	private final PARSE_STYLE	loadReleases;

	/**
	 * The SiteParser for the Label!<br>
	 * <br>
	 * A good example for a Label with all tags and so on: <br>
	 * {@link http://www.metal-archives.com/labels/Metal_Blade_Records/3}.<br>
	 * <br>
	 * 
	 * 
	 * <b>The last 3 parameter...</b><br>
	 * 
	 * currentRooser, pastRooster and releases are PARSE_STYLES.<br>
	 * They can change the behaviour how metal-archives present their data.<br>
	 * You should use PARSE_STYLE.BAND_SEARCH_MODE if care about the data and don't understand the
	 * documentation. <br>
	 * By default the 3 Fields are disabled (PARSE_STYLE.NONE)
	 * 
	 * @param id the ID of the Label to parse for.
	 * @param loadImages if you want are interested in the Label-logo.
	 * @param currentRooster If you care about the current Bands that are used by this Label.
	 * @param pastRooster If you care about the past Bands that were used by this Label.
	 * @param releases If you care about the releases that this Label published.
	 * @throws ExecutionException
	 */
	public LabelSiteParser(final Label label, final boolean loadImages, final boolean loadLinks, final PARSE_STYLE currentRooster, final PARSE_STYLE pastRooster, final PARSE_STYLE releases) throws ExecutionException {
		super(label, loadImages, loadLinks);
		this.loadCurrentRooster = currentRooster;
		this.loadPastRooster = pastRooster;
		this.loadReleases = releases;
	}

	@Override
	public final Label parse() {
		Label label = new Label(this.entity.getId());
		label.setName(parseLabelName());

		// upper part
		label = parseLeftSide(label);
		label = parseRightSide(label);

		// middle part
		label = parseContactData(label);

		// lower part
		label.setCurrentRoster(parseCurrentRoster());
		label.setPastRoster(parsePastRoster());
		label.setReleases(parseReleases());

		label.addLink(parseLinks());
		label.setAdditionalNotes(parseAdditionalNotes());
		final String logoUrl = parseLogoUrl();
		label.setLogoUrl(logoUrl);
		label.setLogo(parseLabelLogo(logoUrl));
		label = parseModfications(label);
		return label;
	}

	private Label parseRightSide(final Label label) {
		String newhtml = this.html.substring(this.html.indexOf("</dl>") + 5);
		String[] upperRightPart = newhtml.substring(0, newhtml.indexOf("</dl>")).split("<dd>");
		label.setStatus(parseLabelStatus(upperRightPart[1]));
		label.setSpecialisation(parseSpecialisedIn(upperRightPart[2]));
		label.setFoundingDate(parseFoundingDate(upperRightPart[3]));
		label.setSubLabels(parseSubLabels(upperRightPart[4]));
		if (upperRightPart[3].contains("<dt>Parent label:</dt>")) {
			label.setParentLabel(parseParentLabel(upperRightPart[4]));
		}
		label.setOnlineShopping(parseHasOnlineShopping(upperRightPart[upperRightPart.length - 1]));
		return label;
	}

	private Label parseLeftSide(final Label label) {
		String[] upperLeftPart = this.html.substring(0, this.html.indexOf("</dl>")).split("<dd");
		label.setAddress(parseAddress(upperLeftPart[1]));
		label.setCountry(parseCountry(upperLeftPart[2]));
		label.setPhoneNumber(parsePhoneNumber(upperLeftPart[3]));
		return label;
	}

	private String parseLabelName() {
		String name = this.html.substring(this.html.indexOf("<h1 class=\"label_name\">") + 22);
		name = name.substring(name.indexOf(">") + 1, name.indexOf("</h1>"));
		return name;
	}

	private Link[] parseLinks() {
		final List<Link> linksFromEntity = this.entity.getLinks();
		if (!linksFromEntity.isEmpty()) {
			Link[] linkArray = new Link[linksFromEntity.size()];
			linksFromEntity.toArray(linkArray);
			return linkArray;
		} else if (this.loadLinks) {
			try {
				final LinkParser parser = new LinkParser(this.entity.getId(), LinkParser.LABEL_PARSER);
				return parser.parse();
			} catch (final ExecutionException e) {
				logger.error("unanble to parse label links from " + this.entity, e);
			}
		}
		return new Link[0];

	}

	private String parseAddress(final String upperLeftPart) {
		String address = upperLeftPart.substring(upperLeftPart.indexOf("> ") + 2, upperLeftPart.indexOf("</dd>"));
		address = MetallumUtil.parseHtmlWithLineSeperators(address);
		return address;
	}

	private Country parseCountry(final String upperLeftPart) {
		String countryStr = upperLeftPart.substring(upperLeftPart.indexOf(">") + 1, upperLeftPart.indexOf("</dd>"));
		return Country.getRightCountryForString(countryStr);
	}

	private String parsePhoneNumber(final String upperLeftPart) {
		String number = upperLeftPart.substring(upperLeftPart.indexOf("> ") + 2, upperLeftPart.indexOf("</dd>")).trim();
		return number;
	}

	private LabelStatus parseLabelStatus(final String upperRightPart) {
		String status = upperRightPart.substring(upperRightPart.indexOf("\">") + 2, upperRightPart.indexOf("</sp"));
		return LabelStatus.getLabelStatusForString(status);
	}

	private String parseSpecialisedIn(final String upperRightPart) {
		String spec = upperRightPart.substring(1, upperRightPart.indexOf(" </dd>"));
		return spec;
	}

	private String parseFoundingDate(final String upperRightPart) {
		String date = upperRightPart.substring(1, upperRightPart.indexOf(" </dd>"));
		return date;
	}

	private Label parseParentLabel(final String upperRightPart) {
		String labelName = upperRightPart.substring(upperRightPart.indexOf("\">") + 2, upperRightPart.indexOf("</a></dd>"));
		String labelId = upperRightPart.substring(0, upperRightPart.indexOf("\">" + labelName));
		labelId = labelId.substring(labelId.lastIndexOf("/") + 1, labelId.length());
		return new Label(Long.parseLong(labelId), labelName);
	}

	private List<Label> parseSubLabels(final String upperRightPart) {
		List<Label> labelList = new ArrayList<Label>();
		// must and with </dd> and at least 9 chracters to match
		if (!upperRightPart.trim().matches(".{9,}?[</dd>]$")) {
			return labelList;
		}
		final String[] labellinks = upperRightPart.split(",");
		for (int i = 0; i < labellinks.length; i++) {
			// prepare, to remove Online Shopping if it appears
			String parseAbleString = labellinks[i].replaceAll("(?imx)</dd>.*", "").trim();
			// name
			String labelName = Jsoup.parse(parseAbleString).text();
			// id
			String labelId = parseAbleString.substring(0, parseAbleString.length() - (labelName.length() + 6));
			labelId = labelId.substring(labelId.lastIndexOf("/") + 1, labelId.length());
			labelList.add(new Label(Long.parseLong(labelId), labelName));
		}
		return labelList;
	}

	private boolean parseHasOnlineShopping(final String upperRightPart) {
		String value = Jsoup.parse(upperRightPart).text();
		if (value.equalsIgnoreCase("Yes")) {
			return true;
		}
		return false;
	}

	/**
	 * Here you'll get all Bands which are currently used by this Label.<br>
	 * <br>
	 * 
	 * <b>ONLY if option is set<b>
	 * 
	 * @return a List of Bands, if there are none, you'll get a empty List.
	 */
	private List<Band> parseCurrentRoster() {
		List<Band> roster = this.entity.getCurrentRoser();
		if (!roster.isEmpty()) {
			return roster;
		} else if (this.loadCurrentRooster != PARSE_STYLE.NONE) {
			try {
				final CurrentRosterParser parser = new CurrentRosterParser(this.entity.getId(), Byte.MAX_VALUE, true, this.loadCurrentRooster);
				return new ArrayList<Band>(parser.parse().values());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return new ArrayList<Band>();
	}

	/**
	 * Can return a Map with the Band entry (as Key) where Band.getId () = 0 & Band.getName(Various
	 * Artists) <br>
	 * You'll get this when the Label also releases a SplitDisc., in the List<Disc> of that band,<br>
	 * which is the SplitDisc you'll get the real Bands.<br>
	 * <br>
	 * 
	 * <b>ONLY if option is set<b>
	 * 
	 * @return the Map with Band as Key and Integer as Value representing the quantum of releases<br>
	 *         If there are none you'll get a empty HashMap.
	 */
	private Map<Band, Integer> parsePastRoster() {
		final Map<Band, Integer> pastRoster = this.entity.getPastRoster();
		if (!pastRoster.isEmpty()) {
			return pastRoster;
		} else if (this.loadPastRooster != PARSE_STYLE.NONE) {
			// try {
			try {
				return new PastRosterParser(this.entity.getId(), Byte.MAX_VALUE, true, this.loadPastRooster).parse();
			} catch (final Exception e) {
				logger.error("unanble to parse past roster with " + this.loadPastRooster + " and " + this.entity, e);
			}
		}
		return new HashMap<Band, Integer>();
	}

	/**
	 * You'll get a Map with Band as Key and Disc as Value.<br>
	 * <br>
	 * To go more into Detail: This method will return all the Discs which are released under this
	 * Label<br>
	 * mapped to the specific Band<br>
	 * <br>
	 * 
	 * <b>ONLY if option is set<b>
	 * 
	 * @return a Map with Band as Key and a List with Discs.
	 */
	private Map<Band, List<Disc>> parseReleases() {
		Map<Band, List<Disc>> releases = this.entity.getReleases();
		if (!releases.isEmpty()) {
			return releases;
		} else if (this.loadReleases != PARSE_STYLE.NONE) {
			try {
				return new ReleaseParser(this.entity.getId(), Byte.MAX_VALUE, true, this.loadReleases).parse();
			} catch (final Exception e) {
				logger.error("unanble to parse label releases with " + this.loadReleases + " and " + this.entity, e);
			}
		}
		return new HashMap<Band, List<Disc>>();
	}

	private String parseAdditionalNotes() {
		String additionalNotes = "";
		if (this.html.contains("<div id=\"label_notes")) {
			additionalNotes = this.html.substring(this.html.indexOf("<div id=\"label_notes"), this.html.indexOf("<div id=\"auditTrail"));
			additionalNotes = additionalNotes.replaceAll("<.?p>", "<br><br>");
			additionalNotes = MetallumUtil.parseHtmlWithLineSeperators(additionalNotes);
		}
		return additionalNotes;
	}

	private Label parseContactData(final Label label) {
		String contactHtml = this.html.substring(this.html.indexOf("<p id=\"label_contact\">") + 22, this.html.length());
		contactHtml = contactHtml.substring(0, contactHtml.indexOf("</p>"));

		label.setWebSiteURL(parseLabelWebsiteURL(contactHtml));
		label.setEmail(parseLabelEmail(contactHtml));
		return label;
	}

	private Link parseLabelWebsiteURL(final String concatHtml) {
		String htmlString = concatHtml;
		if (htmlString.contains("title=\"Email\"")) {
			htmlString = htmlString.substring(0, htmlString.indexOf("title=\"Email\""));
		}
		String webSitename = Jsoup.parse(htmlString).text().trim();
		if (webSitename.isEmpty()) {
			return new Link();
		}
		Link website = new Link();
		website.setName(webSitename);

		String url = htmlString.substring(htmlString.indexOf("<a href=\"") + 9);
		url = url.substring(0, url.indexOf("\""));
		website.setURL(url);
		return website;
	}

	/**
	 * Parses the specific HTML part for the email of the Label.
	 * 
	 * @param htmlPart the specific HTML part from the Contact section
	 * @return the email of the Label if available otherwise an empty String.
	 */
	private String parseLabelEmail(final String htmlPart) {
		String htmlString = htmlPart;
		if (htmlString.contains("title=\"Email\"")) {
			htmlString = htmlString.substring(htmlString.indexOf("title=\"Email\""), htmlString.length());
			String mail = htmlString.substring(0, htmlString.indexOf("</a>"));
			mail = mail.substring(mail.lastIndexOf("\">") + 2, mail.length());
			// the mail is in this structure "hidden":
			// geil@google.com -> moc\elgoog\\lieg
			mail = new StringBuffer(mail).reverse().toString();
			mail = mail.replaceAll("//", "@");
			mail = mail.replaceAll("/", ".");
			return mail;
		}
		return "";
	}

	private final String parseLogoUrl() {
		String logoUrl = null;
		if (this.html.contains("class=\"label_img\"")) {
			logoUrl = this.html.substring(this.html.indexOf("class=\"label_img\"") + 16);
			logoUrl = logoUrl.substring(logoUrl.indexOf("src=\"") + 5);
			logoUrl = logoUrl.substring(0, logoUrl.indexOf("\""));
		}
		return logoUrl;
	}

	/**
	 * If loadImage is true this method tries to download the Label logo.
	 * 
	 * @return null if loadImage is false or if there is no artwork.
	 */
	private final BufferedImage parseLabelLogo(final String logoUrl) {
		if (this.loadImage && logoUrl != null) {
			try {
				return Downloader.getImage(logoUrl);
			} catch (final ExecutionException e) {
				logger.error("Exception while downloading an image from \"" + logoUrl + "\" ," + this.entity, e);
			}
		}
		return null;
	}

	@Override
	protected final String getSiteURL() {
		return MetallumURL.assembleLabelURL(this.entity.getId());
	}

}
