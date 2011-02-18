package gov.nysenate.openleg;

import gov.nysenate.openleg.model.bill.Bill;
import gov.nysenate.openleg.model.bill.BillEvent;
import gov.nysenate.openleg.model.transcript.Transcript;
import gov.nysenate.openleg.model.bill.Vote;
import gov.nysenate.openleg.model.calendar.Calendar;
import gov.nysenate.openleg.model.calendar.Section;
import gov.nysenate.openleg.model.calendar.Supplemental;
import gov.nysenate.openleg.model.committee.Meeting;
import gov.nysenate.openleg.search.Result;
import gov.nysenate.openleg.search.SearchEngine2;
import gov.nysenate.openleg.search.SearchResult;
import gov.nysenate.openleg.search.SearchResultSet;
import gov.nysenate.openleg.search.SenateResponse;
import gov.nysenate.openleg.util.SessionYear;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.lucene.queryParser.ParseException;
import org.codehaus.jackson.map.ObjectMapper;


public class APIServlet extends HttpServlet implements OpenLegConstants {

	private static long DATE_START = SessionYear.getSessionStart();
	private static long DATE_END = SessionYear.getSessionEnd();
	
	private static final long serialVersionUID = -7567155903739799800L;

	private static Logger logger = Logger.getLogger(APIServlet.class);	

	private static final String SRV_DELIM = "/";
	
	private static SearchEngine2 searchEngine = null;
	
	private final static String DEFAULT_SORT_FIELD = "when";
	private final static String DEFAULT_SEARCH_FORMAT = "json";
	private final static String DEFAULT_SESSION_YEAR = "2009";
	
	private final static DateFormat DATE_FORMAT_MED = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);

	//Jackson JSON parser
	private static ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
	
	@Override
	public void init() throws ServletException {
		super.init();
	
		if (searchEngine == null)
			searchEngine = SearchEngine2.getInstance();
		
	}

	public static SearchEngine2 getSearchEngineInstance ()	{
		return searchEngine;
	}
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		if (searchEngine == null)
			searchEngine = SearchEngine2.getInstance();
		
	}
	
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {
		
		if (req.getParameter("reset")!=null) {
			if (searchEngine != null)
				searchEngine.closeSearcher();
			
			searchEngine = SearchEngine2.getInstance();
		}
		
		String encodedUri = req.getRequestURI();
		String uri = java.net.URLDecoder.decode(encodedUri,OpenLegConstants.ENCODING);
			
		logger.info("request: " + uri + " (" + encodedUri + ")");
		
		req.setAttribute(KEY_PATH,uri);
	
		StringTokenizer st = new StringTokenizer (uri,SRV_DELIM);
		
		st.nextToken(); //remove the 'legislation'
		String service = st.nextToken(); //api
		String version = "";
		
		if (st.hasMoreTokens())
			version = st.nextToken(); //1.0

		int pageIdx = DEFAULT_START_PAGE;
		int pageSize = DEFAULT_PAGE_SIZE;
		
		try
		{	
			if (version.equals("1.0") || version.equals("html")) {
				String format = null;
				
				if(version.equals("html"))
					format = version;
				else 
					format = st.nextToken().toLowerCase();	
				
				String type = st.nextToken().toLowerCase();
				String key = "";
				
				req.setAttribute(KEY_TYPE,type);
				
				if (st.hasMoreTokens())
					key = URLDecoder.decode(st.nextToken(),OpenLegConstants.ENCODING);
			
				if (st.hasMoreTokens())
				{
					pageIdx = Integer.parseInt(st.nextToken());
						
						if (st.hasMoreTokens())
							pageSize = Integer.parseInt(st.nextToken());
						else {
							pageSize = pageIdx;
							pageIdx = Integer.parseInt(key);
							key = "";
						}
				}
				else if (format.equals(FORMAT_XML)) //for now with XML
					pageSize = DEFAULT_API_PAGE_SIZE;
				
				if (type.equalsIgnoreCase("sponsor"))
				{
					String filter = req.getParameter("filter");
					key = "sponsor:\"" + key + (filter != null ? " AND " + filter : "");
					type = "bills";
				}
				else if (type.equalsIgnoreCase("committee"))
				{
					key = "committee:\"" + key + "\"";
					type = "bills";
				}
				
				handleAPIv1(format, type, key, pageIdx, pageSize, req, resp);
			}
			else if (service.equals("api")) { // /legislation/api/csv/bill/S1399
				String format = version;
				String type = URLDecoder.decode(st.nextToken(),OpenLegConstants.ENCODING);
				String key = URLDecoder.decode(st.nextToken(),OpenLegConstants.ENCODING);
				
				req.setAttribute(KEY_TYPE,type);
	
				
				if (st.hasMoreTokens()) {
					pageIdx = Integer.parseInt(st.nextToken());
						
						if (st.hasMoreTokens())
							pageSize = Integer.parseInt(st.nextToken());
							
				}
				else if (format.equals(FORMAT_XML)) //for now with XML
					pageSize = DEFAULT_API_PAGE_SIZE;
					
				handleAPIv1(format, type, key, pageIdx, pageSize, req, resp);
			}
			else 
			{
				String format = "html";
				
				if (req.getSession().getAttribute("mobile")!=null)
					format = "mobile";
				
				String type = service;
				String key = URLDecoder.decode(version,OpenLegConstants.ENCODING);
				
				if (key.length()==0 && req.getParameterNames().hasMoreElements())
					key = req.getParameterNames().nextElement().toString();
				
				if (type.equalsIgnoreCase("sponsor")) {
					
					key = "sponsor:\"" + key + "\"";
					type = "bills";
				}
				else if (type.equalsIgnoreCase("committee")) {
					key = "committee:\"" + key + "\"";
					type = "bills";
				}
				
				req.setAttribute(KEY_TYPE,type);
				
					if (st.hasMoreTokens()) {
						pageIdx = Integer.parseInt(st.nextToken());
							
							if (st.hasMoreTokens()) {
								pageSize = Integer.parseInt(st.nextToken());
								
							}
					}
					else if (format.equals(FORMAT_XML)) //for now with XML
						pageSize = DEFAULT_API_PAGE_SIZE;
					
					handleAPIv1(format, type, key, pageIdx, pageSize, req, resp);
			}
		}
		catch (NumberFormatException nfe) {
			logger.warn ("Invalid API call", nfe);
		}
		catch (NoSuchElementException nse) {
			logger.warn ("Invalid API call", nse);
		}
		catch (NullPointerException npe) {
			logger.warn ("Invalid API call", npe);
		}
		catch (Exception e)	{
			logger.warn ("Invalid API call", e);
		}
		
	}
	

	// /openleg/api/1.0/html/bill/A9067/1/5
	public void handleAPIv1 (String format, String type, String key, int pageIdx, int pageSize, HttpServletRequest req, HttpServletResponse resp) 
		throws IOException, ServletException {

		String viewPath = "";
		String originalType = type;
		String sFormat = "json";
		String sortField = type.contains("bill") ? "sortindex " : "when";
		SenateResponse sr = null;
		
		key = key.trim();
		
		if (pageSize > MAX_PAGE_SIZE)
			throw new ServletException ("The maximum page size is " + MAX_PAGE_SIZE);
		//now calculate start, end idx based on pageIdx and pageSize
		int start = (pageIdx - 1) * pageSize;
		int end = start + pageSize;
				
		logger.info("request: key=" + key + ";type=" + type + ";format=" + format + ";paging=" + start + "/" + end);
		try	{
			String searchString = "";
			
			if (type != null) {
				
				if (type.endsWith("s"))	{
					type = type.substring(0,type.length()-1);
					
					searchString = "otype:" + type;
					
					if (key != null && key.length() > 0)
						searchString += " AND " + key;
				}
				else {
					searchString ="otype:" + type;
	
					if (key != null && key.length() > 0) {
						if (type.equals("bill") && key.indexOf("-") == -1)
							key += "-" + DEFAULT_SESSION_YEAR;
	
						key = key.replace(" ","+");
						searchString += " AND oid:" + key;
					}
				}
				
			}
			else
			{
				searchString = key;
			}
			
			
			
			
			req.setAttribute("sortField", sortField);
			req.setAttribute("type", type);
			req.setAttribute("term", searchString);
			req.setAttribute("format", format);
			req.setAttribute("key", key);
			req.setAttribute(PAGE_IDX,pageIdx+"");
			req.setAttribute(PAGE_SIZE,pageSize+"");
						
			
			if(originalType.equals("bills")) {
				sr = searchEngine.search(dateReplace(searchString) 
						+ " AND year:" + SessionYear.getSessionYear() 
						+ " AND active:true",
						sFormat,start,pageSize,sortField,true);
			}
			else if(originalType.endsWith("s")) {
				sr = searchEngine.search(dateReplace(searchString) 
						+ " AND when:[" 
						+ DATE_START 
						+ " TO " 
						+ DATE_END + "]"
						+ " AND active:true",
						sFormat,start,pageSize,sortField,true);
			}
			else {
				sr = searchEngine.search(dateReplace(searchString),sFormat,start,pageSize,sortField,true);
			}
			
			logger.info("got search results: " + sr.getResults().size());
						
			if (sr.getResults().size()==0) {
				resp.sendError(404);
				return;
			}
			else if (sr.getResults().size()==1 && (format.equals("html") || format.equals("html-print") || format.equals("lrs-print")) || format.equals("mobile"))	{
				Result result = sr.getResults().get(0);
				
				if (!result.getOid().equals(key)) {
					resp.sendRedirect("/legislation/api/1.0/" + format + "/" + result.getOtype() + "/" + result.getOid());
					return;
				}
				
				String jsonData = result.getData();
				req.setAttribute("active", result.getActive());
								
				jsonData = jsonData.substring(jsonData.indexOf(":")+1);
				jsonData = jsonData.substring(0,jsonData.lastIndexOf("}"));
				
				String className = "gov.nysenate.openleg.model.bill." + type.substring(0,1).toUpperCase() + type.substring(1);
				
				if (type.equals("bill"))
				{
					String billQueryId = key;
					String sessionYear = DEFAULT_SESSION_YEAR;
					
					String[] billParts = billQueryId.split("-");
					billQueryId = billParts[0];
					if (billParts.length > 1)
						sessionYear = billParts[1];
				
					String billWildcard = billQueryId;
					if (!Character.isDigit(billWildcard.charAt(billWildcard.length()-1)))
						billWildcard = billWildcard.substring(0,billWildcard.length()-1);
						
					//get BillEvents for this 
					//otype:action AND oid:A10234A-*
					String rType = "action";
					String rQuery = null;
					//String rQuery = "oid:" + billQueryId + "-" + sessionYear + "-*";
					rQuery = "" + billWildcard + "-" + sessionYear;
					
					logger.info(rQuery);
					
					rQuery = "billno:((" 
						+ rQuery 
	                        + " OR [" + billWildcard + "A-" + sessionYear 
	                           + " TO " + billWildcard + "Z-" + sessionYear
	                        + "]) AND " + billWildcard + "*-" + sessionYear + ")";

					ArrayList<SearchResult> relatedActions = getRelatedSenateObjects (rType,rQuery);
					Hashtable<String,SearchResult> uniqResults = new Hashtable<String,SearchResult>();
					for (SearchResult rResult: relatedActions) {
						BillEvent rAction = (BillEvent)rResult.getObject();
						uniqResults.put(rAction.getEventDate().toGMTString()+'-'+rResult.getTitle().toUpperCase(), rResult);
						
					}
					
					ArrayList<SearchResult> list = Collections.list(uniqResults.elements());
					Collections.sort(list);
					req.setAttribute("related-" + rType, list);

					//get Meetings
					//otype:meeting AND ojson:"S67005"
					rType = "bill";
					//rQuery = "oid:[" + startBill + " TO " + endBill + "]";		
					rQuery = "oid:" + billWildcard + "-" + sessionYear;
					
					logger.info(rQuery);
					
					rQuery = "oid:((" 
						+ rQuery 
	                        + " OR [" + billWildcard + "A-" + sessionYear 
	                           + " TO " + billWildcard + "Z-" + sessionYear
	                        + "]) AND " + billWildcard + "*-" + sessionYear + ")";
					
					req.setAttribute("related-" + rType, getRelatedSenateObjects (rType,rQuery));

					//get Meetings
					//otype:meeting AND bills:"S67005-2011"
					rType = "meeting";
					rQuery = "bills:" + key;					
					req.setAttribute("related-" + rType, getRelatedSenateObjects (rType,rQuery));
					
					//get calendars
					//otype:calendar AND  bills:"S337A-2011"
					rType = "calendar";
					 rQuery = "bills:\"" + key + "\"";
					req.setAttribute("related-" + rType, getRelatedSenateObjects (rType,rQuery));
					
					//get votes
					//otype:vote AND billno:"A11597-2011"
					rType = "vote";
					 rQuery = "billno:\"" + key + "\"";
					 					 
					req.setAttribute("related-" + rType, getRelatedSenateObjects (rType,rQuery));
				}
				else if (type.equals("calendar")) {
					className = "gov.nysenate.openleg.model.calendar.Calendar";
				}
				else if (type.equals("meeting")) {
					className = "gov.nysenate.openleg.model.committee.Meeting";
				}
				else if(type.equals("transcript")) {
					className = "gov.nysenate.openleg.model.transcript.Transcript";
				}
				
				Object resultObj = null;
				
				try	{
					resultObj = mapper.readValue(jsonData,  Class.forName(className));
				}
				catch (Exception e) {
					logger.warn("error binding className", e);
				}
				
				req.setAttribute(type, resultObj);
				viewPath = "/views/" + type + "-" + format + ".jsp";
			}
			else {
				if (type.equals("bill") && (!format.equals("html"))) {
					viewPath = "/views/bills-" + format + ".jsp";
					
					ArrayList<SearchResult> searchResults = buildSearchResultList(sr);
					ArrayList<Bill> bills = new ArrayList<Bill>();
					
					for (SearchResult result : searchResults) {
						bills.add((Bill)result.getObject());
					}
										
					req.setAttribute("bills", bills);
				}
				else
				{
					viewPath = "/views/" + "search" + "-" + format + ".jsp";
					
					SearchResultSet srs = new SearchResultSet();
					srs.setTotalHitCount((Integer)sr.getMetadata().get("totalresults"));
					srs.setResults(buildSearchResultList(sr));
					
					req.setAttribute("results", srs);
				}
			}
		}
		catch (Exception e) {
			logger.warn("search controller didn't work for: " + req.getRequestURI(),e);
			e.printStackTrace();
		}

		try {
			logger.info("routing to search controller:" + viewPath);
			getServletContext().getRequestDispatcher(viewPath).forward(req, resp);
		}
		catch (Exception e)	{
			logger.warn("search controller didn't work for: " + req.getRequestURI(),e);
		}
	}
	
	private ArrayList<SearchResult> getRelatedSenateObjects (String type, String query) 
			throws ParseException, IOException, ClassNotFoundException {		
		
		StringBuffer searchString = new StringBuffer();
		searchString.append("otype:");
		searchString.append(type);
		searchString.append(" AND ");
		searchString.append("(");
		searchString.append(query);
		searchString.append(")");
						
		int start = 0;
		int pageSize = 100;
		
		SenateResponse sr = searchEngine.search(
				dateReplace(
						searchString.toString()),
						DEFAULT_SEARCH_FORMAT,
						start,
						pageSize,
						DEFAULT_SORT_FIELD,
						true);

		return buildSearchResultList(sr);
	}
	
	
	public static ArrayList<SearchResult> buildSearchResultList (SenateResponse sr)
			throws ClassNotFoundException {
		ArrayList<SearchResult> srList = new ArrayList<SearchResult>();
		
		for (Result newResult : sr.getResults()) {
			try	{
				SearchResult sResult = new SearchResult();
				sResult.setId(newResult.getOid());
				sResult.setLastModified(new Date(newResult.getLastModified()));
				sResult.setScore(1.0f);
				
				String type = newResult.getOtype();
				String jsonData = newResult.getData();
				
				if (jsonData == null)
					continue;
				
				jsonData = jsonData.substring(jsonData.indexOf(":")+1);
				jsonData = jsonData.substring(0,jsonData.lastIndexOf("}"));
				
				String className = "gov.nysenate.openleg.model.bill." + type.substring(0,1).toUpperCase() + type.substring(1);
				if (type.equals("calendar"))
					className = "gov.nysenate.openleg.model.calendar.Calendar";
				else if (type.equals("meeting"))
					className = "gov.nysenate.openleg.model.committee.Meeting";
				else if (type.equals("action"))
					className = "gov.nysenate.openleg.model.bill.BillEvent";
				else if(type.equals("transcript"))
					className = "gov.nysenate.openleg.model.transcript.Transcript";
				
				Object resultObj = null;
				try	{
					resultObj = mapper.readValue(jsonData,  Class.forName(className));
					sResult.setObject(resultObj);
				}
				catch (Exception e)	{
					logger.warn("error binding:"+ className, e);
				}
				
				if (resultObj == null)
					continue;
				
				String title = "";
				String summary = "";
				
				HashMap<String,String> fields = new HashMap<String,String>();
				fields.put("type", type);
				
				if (type.equals("bill")) {
					Bill bill = (Bill)resultObj;
										
					if (bill.getTitle() != null)
						title += bill.getTitle();
					else
						title += "(no title)";
					
					if (bill.getSponsor()!=null)
						fields.put("sponsor",bill.getSponsor().getFullname());
					
					summary = bill.getSummary();
					
					fields.put("committee", bill.getCurrentCommittee());
					fields.put("billno", bill.getSenateBillNo());
					fields.put("summary", bill.getSummary());
					fields.put("year", bill.getYear()+"");
				}
				else if (type.equals("calendar")) {
					Calendar calendar = (Calendar)resultObj;
					
					title = calendar.getNo() + "-" + calendar.getYear();
	
					if (calendar.getType() == null)
						fields.put("type", "");
					else if (calendar.getType().equals("active"))
						fields.put("type", "Active List");
					else if (calendar.getType().equals("floor"))
						fields.put("type", "Floor Calendar");
					else
						fields.put("type", calendar.getType());
						
					
					Supplemental supp = calendar.getSupplementals().get(0);
					
					if (supp.getCalendarDate()!=null) {
						fields.put("date", supp.getCalendarDate().toLocaleString());
						
						summary = "";
						
						if (supp.getSections() != null) {
							Iterator<Section> itSections = supp.getSections().iterator();
							while (itSections.hasNext()) {
								Section section = itSections.next();
								
								summary += section.getName() + ": ";
								summary += section.getCalendarEntries().size() + " items;";
							}
						}
					}
					else if (supp.getSequence()!=null) {
						
						fields.put("date", supp.getSequence().getActCalDate().toLocaleString());
						
						summary = supp.getSequence().getCalendarEntries().size() + " item(s)";
					}
				}
				else if (type.equals("transcript")) {
					Transcript transcript = (Transcript)resultObj;
										
					if (transcript.getTimeStamp() != null)
						title = transcript.getTimeStamp().toLocaleString();
					else
						title = "Transcript - " + transcript.getLocation();
					
					summary = transcript.getType() + ": " + transcript.getLocation();
						
					fields.put("location", transcript.getLocation());
	
				}
				else if (type.equals("meeting")) {
					Meeting meeting = (Meeting)resultObj;
					title = meeting.getCommitteeName() + " (" + meeting.getMeetingDateTime().toLocaleString() + ")";
					
					fields.put("location", meeting.getLocation());
					fields.put("chair", meeting.getCommitteeChair());
					fields.put("committee", meeting.getCommitteeName());
					
					summary = meeting.getNotes();
				}
				else if (type.equals("action")) {
					BillEvent billEvent = (BillEvent)resultObj;
					String billId = billEvent.getBillId();
					
					title = billEvent.getEventText();
					
					fields.put("date", DATE_FORMAT_MED.format(billEvent.getEventDate()));
					fields.put("billno", billId);
				}
				else if (type.equals("vote")) {
					Vote vote = (Vote)resultObj;
					
					if (vote.getVoteType() == Vote.VOTE_TYPE_COMMITTEE)
						fields.put("type","Committee Vote");
					else if (vote.getVoteType() == Vote.VOTE_TYPE_FLOOR)
						fields.put("type","Floor Vote");
					
					if (vote.getBill() != null) {
						Bill bill = vote.getBill();
												
						if (bill.getSponsor()!=null)
							fields.put("sponsor",bill.getSponsor().getFullname());
					
						if (vote.getVoteType() == Vote.VOTE_TYPE_COMMITTEE)
							fields.put("committee", bill.getCurrentCommittee());
	
						fields.put("billno", bill.getSenateBillNo());
						fields.put("year", bill.getYear()+"");
					}
					
					title += vote.getVoteDate().toLocaleString();
					
					summary = vote.getDescription();
				}
				
				sResult.setTitle(title);
				sResult.setSummary(summary);
				sResult.setType(newResult.getOtype());
				sResult.setFields(fields);
				srList.add(sResult);
			}
			catch (Exception e)
			{
				logger.warn("problem parsing result: " + newResult.otype + "-" + newResult.oid, e);
			}
		}
		
		return srList;
	}
	
	
	public static String dateReplace(String term) throws ParseException {
		Pattern  p = Pattern.compile("(\\d{1,2}[-]?){2}(\\d{2,4})T\\d{2}-\\d{2}");
		Matcher m = p.matcher(term);
		
		SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy'T'KK-mm");
		
		while(m.find()) {
			String d = term.substring(m.start(),m.end());
			
			Date date = null;
			try {
				date = sdf.parse(d);
				term = term.substring(0, m.start()) + date.getTime() + term.substring(m.end());
			} catch (java.text.ParseException e) {
				logger.warn(e);
			}
			
			m.reset(term);
			
		}
		
		return term;
	}
	
	
	
}
