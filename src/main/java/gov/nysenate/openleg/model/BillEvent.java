package gov.nysenate.openleg.model;

import gov.nysenate.openleg.PMF;
import gov.nysenate.openleg.lucene.LuceneObject;
import gov.nysenate.openleg.util.DocumentBuilder;

import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import javax.jdo.annotations.Cacheable;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.lucene.document.Field;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@PersistenceCapable
@XmlRootElement
@Cacheable
@XStreamAlias("billevent")
public class BillEvent extends SenateObject implements LuceneObject
{

	@Persistent 
    @PrimaryKey
	@Column(name="bill_event_id")
    private String billEventId;

	@Persistent
	@Column(name="event_date")
	private Date eventDate;
	
	@Persistent
	@Column(name="event_text")
	private String eventText;
	

	public BillEvent (Bill bill, Date eventDate, String eventText)
	{
		this.eventDate = eventDate; 
		this.eventText = eventText;
		
		try
		{
			this.billEventId = bill.getSenateBillNo() + "-" + eventDate.getTime() + "-" + URLEncoder.encode(eventText,"utf-8");
		}
		catch (Exception e)
		{
			//foo
		}
	}
	
	public String getBillId ()
	{
		return billEventId.substring(0,billEventId.indexOf("-"));
	}
	/**
	 * @return the eventDate
	 */
	public Date getEventDate() {
		return eventDate;
	}
	/**
	 * @param eventDate the eventDate to set
	 */
	public void setEventDate(Date eventDate) {
		this.eventDate = eventDate;
	}
	/**
	 * @return the eventText
	 */
	public String getEventText() {
		return eventText;
	}
	/**
	 * @param eventText the eventText to set
	 */
	public void setEventText(String eventText) {
		this.eventText = eventText;
	}
	
	/**
	 * @return the billEventId
	 */
	public String getBillEventId() {
		return billEventId;
	}

	/**
	 * @param billEventId the billEventId to set
	 */
	public void setBillEventId(String billEventId) {
		this.billEventId = billEventId;
	}
	
	@Override
	public boolean equals(Object obj) {
		
		if (obj != null && obj instanceof BillEvent)
		{
			String thisId = getBillEventId();
			String thatId =  ((BillEvent)obj).getBillEventId();
			
			return (thisId.equals(thatId));
		}
		
		return false;
	}
	
	@Override
	public HashMap<String, Field> luceneFields() {
		HashMap<String,Field> fields = new HashMap<String,Field>();
		
		Bill bill = PMF.getDetachedBill(getBillId());
		
		fields.put("when", new Field("when",eventDate.getTime()+"", DocumentBuilder.DEFAULT_STORE, DocumentBuilder.DEFAULT_INDEX));
		fields.put("billno", new Field("billno",getBillId(), DocumentBuilder.DEFAULT_STORE, DocumentBuilder.DEFAULT_INDEX));
				
		try
		{
			if (bill.getSponsor()!=null) {
    			fields.put("sponsor", new Field("sponsor",bill.getSponsor().getFullname(), DocumentBuilder.DEFAULT_STORE, DocumentBuilder.DEFAULT_INDEX));
    		}
    		
            if (bill.getCoSponsors()!=null) {
            	StringBuilder cosponsor = new StringBuilder();
            	Iterator<Person> itCosp = bill.getCoSponsors().iterator();
            	
            	while (itCosp.hasNext()) {
            		cosponsor.append((itCosp.next()).getFullname());
            		
            		if (itCosp.hasNext())
            			cosponsor.append(", ");
            	}
            	
            	fields.put("cosponsors", new Field("cosponsors",cosponsor.toString(), DocumentBuilder.DEFAULT_STORE, DocumentBuilder.DEFAULT_INDEX));
            }
		}
		catch (Exception e)
		{
			
		}
		
		return fields;
	}

	@Override
	public String luceneOid() {
		return billEventId;
	}

	@Override
	public String luceneOsearch() {
		Bill bill = PMF.getDetachedBill(getBillId());
		
		StringBuilder searchContent = new StringBuilder();
		searchContent.append(getBillId()).append(" ");
		
		try
		{
			if (bill.getSponsor()!=null) {
    			searchContent.append(bill.getSponsor().getFullname()).append(" ");
    		}
		}
		catch (Exception e)
		{
			
		}
		
		searchContent.append(eventText);
		
		return eventText.toString();
	}

	@Override
	public String luceneOtype() {
		return "action";
	}

	@Override
	public String luceneSummary() {
		return java.text.DateFormat.getDateInstance(DateFormat.MEDIUM).format(eventDate);
	}

	@Override
	public String luceneTitle() {
		return eventText;
	}
}
	
	

