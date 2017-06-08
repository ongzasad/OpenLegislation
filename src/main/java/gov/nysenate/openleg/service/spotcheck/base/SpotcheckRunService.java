package gov.nysenate.openleg.service.spotcheck.base;

import com.google.common.collect.*;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import gov.nysenate.openleg.config.Environment;
import gov.nysenate.openleg.model.spotcheck.ReferenceDataNotFoundEx;
import gov.nysenate.openleg.model.spotcheck.SpotCheckRefType;
import gov.nysenate.openleg.model.spotcheck.SpotCheckReferenceEvent;
import gov.nysenate.openleg.model.spotcheck.SpotCheckReport;
import gov.nysenate.openleg.service.spotcheck.agenda.AgendaReportService;
import gov.nysenate.openleg.service.spotcheck.billtext.BillTextReportService;
import gov.nysenate.openleg.service.spotcheck.calendar.CalendarReportService;
import gov.nysenate.openleg.service.spotcheck.daybreak.DaybreakReportService;
import gov.nysenate.openleg.service.spotcheck.senatesite.agenda.AgendaReportServices;
import gov.nysenate.openleg.service.spotcheck.senatesite.bill.BillReportService;
import gov.nysenate.openleg.service.spotcheck.senatesite.calendar.SenateSiteCalendarReportService;
import gov.nysenate.openleg.util.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import java.time.LocalDateTime;

import static gov.nysenate.openleg.model.spotcheck.SpotCheckRefType.*;

/**
 * Runs spotcheck reports based on scheduling and events
 */
@Service
public class SpotcheckRunService {

    private static final Logger logger = LoggerFactory.getLogger(SpotcheckRunService.class);

    @Autowired private Environment env;

    @Autowired private EventBus eventBus;

    @Autowired private SpotCheckNotificationService spotCheckNotificationService;

    /** A multimap of reports that run whenever pertinent references are generated */
    SetMultimap<SpotCheckRefType, SpotCheckReportService> eventTriggeredReports;

    /** --- Report Services --- */

    /** Agenda Report Services */
    @Autowired private AgendaReportService agendaReportService;

    /** Bill Report Services */
    @Autowired private DaybreakReportService daybreakReportService;
    @Autowired private BillTextReportService billTextReportService;

    /** Calendar Report Services */
    @Autowired private CalendarReportService calendarReportService;

    /** Nysenate.gov Report Services */
    @Autowired private BillReportService senSiteBillReportService;
    @Autowired private SenateSiteCalendarReportService senSiteCalReportService;
    @Autowired private AgendaReportServices senSiteAgendaReportService;

    @PostConstruct
    public void init() {
        eventBus.register(this);
        eventTriggeredReports = ImmutableSetMultimap.<SpotCheckRefType, SpotCheckReportService>builder()
                .put(LBDC_AGENDA_ALERT, agendaReportService)
                .put(LBDC_DAYBREAK, daybreakReportService)
                .put(LBDC_SCRAPED_BILL, billTextReportService)
                .put(LBDC_CALENDAR_ALERT, calendarReportService)
                .put(SENATE_SITE_BILLS, senSiteBillReportService)
                .put(SENATE_SITE_CALENDAR, senSiteCalReportService)
                .put(SENATE_SITE_AGENDA,senSiteAgendaReportService)
                .build();
    }

    /**
     * Given a spotcheck reference event, runs all reports that use the event's spotcheck reference type
     * @param referenceEvent SpotCheckReferenceEvent
     */
    @Subscribe
    public synchronized void handleSpotcheckReferenceEvent(SpotCheckReferenceEvent referenceEvent) {
        runReports(referenceEvent.getRefType());
    }

    /**
     * Run all reports that use the give reference type for the given date time range
     *
     * @param refType SpotCheckRefType
     * @param reportRange Range<LocalDateTime>
     */
    public synchronized void runReports(SpotCheckRefType refType, Range<LocalDateTime> reportRange) {
        eventTriggeredReports.get(refType)
                .forEach(reportService -> runReport(reportService, reportRange));
    }

    /**
     * Run all reports that use the given reference type
     *
     * @param refType SpotCheckRefType
     */
    public synchronized void runReports(SpotCheckRefType refType) {
        runReports(refType, DateUtils.ALL_DATE_TIMES);
    }

    /** --- Internal Methods --- */

    private <T> void runReport(SpotCheckReportService<T> reportService, Range<LocalDateTime> reportRange) {
        logger.info("Attempting to run a {} report..", reportService.getSpotcheckRefType());
        try {
            SpotCheckReport<T> report = reportService.generateReport(
                    DateUtils.startOfDateTimeRange(reportRange), DateUtils.endOfDateTimeRange(reportRange));
            int notesCutoff = 140; // Cut off the notes in the display after this many characters
            logger.info("Saving report: {} {} {}", report.getReportDateTime(), report.getReferenceType(),
                    report.getNotes() != null
                            ? StringUtils.substring(report.getNotes(), 0, notesCutoff) +
                                (report.getNotes().length() > notesCutoff ? "..." : "")
                            : "");
            reportService.saveReport(report);
            spotCheckNotificationService.spotcheckCompleteNotification(report);
        } catch (ReferenceDataNotFoundEx ex) {
            logger.info("No report generated: no {} references could be found. Message: " + ex.getMessage(), reportService.getSpotcheckRefType());
        } catch (Exception ex) {
            spotCheckNotificationService.handleSpotcheckException(ex, true);
        }
    }
}
