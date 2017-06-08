package gov.nysenate.openleg.service.spotcheck.senatesite.calendar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nysenate.openleg.client.view.bill.BillIdView;
import gov.nysenate.openleg.client.view.calendar.CalendarIdView;
import gov.nysenate.openleg.model.base.SessionYear;
import gov.nysenate.openleg.model.base.Version;
import gov.nysenate.openleg.model.bill.BillId;
import gov.nysenate.openleg.model.calendar.CalendarId;
import gov.nysenate.openleg.model.calendar.CalendarType;
import gov.nysenate.openleg.model.spotcheck.senatesite.SenateSiteDump;
import gov.nysenate.openleg.model.spotcheck.senatesite.SenateSiteDumpFragment;
import gov.nysenate.openleg.model.spotcheck.senatesite.bill.SenateSiteBill;
import gov.nysenate.openleg.model.spotcheck.senatesite.calendar.SenateSiteCalendar;
import gov.nysenate.openleg.processor.base.ParseError;
import gov.nysenate.openleg.service.spotcheck.senatesite.base.JsonParser;
import gov.nysenate.openleg.util.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by PKS on 2/25/16.
 */

@Service
public class CalendarJsonParser extends JsonParser {
    @Autowired
    ObjectMapper objectMapper;

    public List<SenateSiteCalendar> parseCalendars(SenateSiteDump calendarDump) throws ParseError {
        return calendarDump.getDumpFragments().stream()
                .flatMap(fragment -> extractCalendarsFromFragment(fragment).stream())
                .collect(Collectors.toList());
    }

    private List<SenateSiteCalendar> extractCalendarsFromFragment(SenateSiteDumpFragment fragment) throws ParseError {
        try {
            JsonNode calendarMap = objectMapper.readTree(fragment.getFragmentFile())
                    .path("nodes");
            if (calendarMap.isMissingNode()) {
                throw new ParseError("Could not locate \"nodes\" node in senate site calendar dump fragment file: " +
                        fragment.getFragmentFile().getAbsolutePath());
            }
            List<SenateSiteCalendar> calendars = new LinkedList<>();
            for (JsonNode calendarNode : calendarMap) {
                CalendarId calendarId = getCalendarId(calendarNode);
                for (JsonNode subCalNode : calendarNode.path("field_ol_cal")) {
                    JsonNode subCalNodeValue = subCalNode.elements().next().path("value");
                    calendars.add(extractSenSiteCalendar(subCalNodeValue, calendarId, fragment));
                }
            }
            return calendars;
        } catch (IOException | NoSuchElementException ex) {
            throw new ParseError("error while reading senate site calendar dump fragment file: " +
                    fragment.getFragmentFile().getAbsolutePath(),
                    ex);
        }
    }

    private SenateSiteCalendar extractSenSiteCalendar(JsonNode subCalNode, CalendarId calendarId,
                                                      SenateSiteDumpFragment fragment) throws IOException {
        SenateSiteCalendar calendar = new SenateSiteCalendar(DateUtils.endOfDateTimeRange(fragment.getDumpId().getRange()));

        calendar.setBillCalNumbers(getIntListValue(subCalNode, "field_ol_bill_cal_number"));
        calendar.setCalendarType(getCalendarType(getValue(subCalNode,"field_ol_type")));
        calendar.setSequenceNo(getIntValue(subCalNode,"field_ol_sequence_no"));
        calendar.setVersion(getVersion(getValue(subCalNode,"field_ol_version")));
        calendar.setCalendarId(calendarId);
        TypeReference<List<String>> listTypeReference = new TypeReference<List<String>>() {};
        List<String> printNos = getStringListValue(subCalNode, "field_ol_bill");
        calendar.setBill(getBillId(printNos, calendar.getCalendarId().getYear()));

       return calendar;
    }

    private CalendarType getCalendarType(String calendarType)
    {
        return CalendarType.valueOf(StringUtils.upperCase(calendarType));
    }

    private Version getVersion(String version)

    {
        return Version.of(version);
    }

    private List<BillId> getBillId(List<String> billNos, int year){
        List<BillId> billId = billNos.stream().map(billNo -> new BillId(billNo, SessionYear.of(year))).collect(Collectors.toList());
        return billId;
    }

    private CalendarId getCalendarId(JsonNode calendarNode){
        int calendarYear = getIntValue(calendarNode, "field_ol_year");
        int calendarNo = getIntValue(calendarNode, "field_ol_cal_no");
        return new CalendarId(calendarNo, calendarYear);
    }
}
