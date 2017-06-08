package gov.nysenate.openleg.service.spotcheck.senatesite.bill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import gov.nysenate.openleg.client.view.base.ListView;
import gov.nysenate.openleg.client.view.bill.BillActionView;
import gov.nysenate.openleg.client.view.bill.BillIdView;
import gov.nysenate.openleg.client.view.bill.BillStatusView;
import gov.nysenate.openleg.client.view.entity.MemberView;
import gov.nysenate.openleg.model.bill.BillAction;
import gov.nysenate.openleg.model.bill.BillId;
import gov.nysenate.openleg.model.spotcheck.senatesite.*;
import gov.nysenate.openleg.model.spotcheck.senatesite.bill.SenateSiteBill;
import gov.nysenate.openleg.processor.base.ParseError;
import gov.nysenate.openleg.util.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import gov.nysenate.openleg.service.spotcheck.senatesite.base.JsonParser;

import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BillJsonParser extends JsonParser{

    @Autowired ObjectMapper objectMapper;

    public List<SenateSiteBill> parseBills(SenateSiteDump billDump) throws ParseError {
        return billDump.getDumpFragments().stream()
                .flatMap(fragment -> extractBillsFromFragment(fragment).stream())
                .collect(Collectors.toList());
    }

    /** --- Internal Methods --- */

    private List<SenateSiteBill> extractBillsFromFragment(SenateSiteDumpFragment fragment) throws ParseError {
        try {
            JsonNode billMap = objectMapper.readTree(fragment.getFragmentFile())
                    .path("nodes");
            if (billMap.isMissingNode()) {
                throw new ParseError("Could not locate \"nodes\" node in senate site bill dump fragment file: " +
                        fragment.getFragmentFile().getAbsolutePath());
            }
            List<SenateSiteBill> bills = new LinkedList<>();
            for (JsonNode billNode : billMap) {
                bills.add(extractSenSiteBill(billNode, fragment));
            }
            return bills;
        } catch (IOException | NoSuchElementException ex) {
            throw new ParseError("error while reading senate site bill dump fragment file: " +
                    fragment.getFragmentFile().getAbsolutePath(),
                    ex);
        }
    }

    private SenateSiteBill extractSenSiteBill(JsonNode billNode, SenateSiteDumpFragment fragment) throws IOException {
        SenateSiteBill bill = new SenateSiteBill(DateUtils.endOfDateTimeRange(fragment.getDumpId().getRange()));

        bill.setBasePrintNo(getValue(billNode, "field_ol_base_print_no"));
        bill.setActiveVersion(getValue(billNode, "field_ol_active_version"));
        bill.setMilestones(getMilestones(billNode, "field_ol_all_statuses"));
        bill.setChamber(getValue(billNode, "field_ol_chamber"));
        bill.setCoSponsors(getMembers(billNode, "field_ol_co_sponsor_names"));
        bill.setText(getValue(billNode, "field_ol_full_text"));
        bill.setAmended(getBooleanValue(billNode, "field_ol_is_amended"));
        bill.setLatestStatusCommittee(getValue(billNode, "field_ol_latest_status_committee"));
        bill.setLawCode(getValue(billNode, "field_ol_law_code"));
        bill.setLawSection(getValue(billNode, "field_ol_law_section"));
        bill.setMemo(getValue(billNode, "field_ol_memo"));
        bill.setMultiSponsors(getMembers(billNode, "field_ol_multi_sponsor_names"));
        bill.setTitle(getValue(billNode, "field_ol_name"));
        bill.setPreviousVersions(getBillIdList(billNode, "field_ol_previous_versions"));
        bill.setPrintNo(getValue(billNode, "field_ol_print_no"));
        bill.setPublishDate(getDateTimeValue(billNode, "field_ol_publish_date"));
        bill.setSameAs(getBillIdList(billNode, "field_ol_same_as"));
        bill.setSponsor(getValue(billNode, "field_ol_sponsor_name"));
        bill.setSummary(getValue(billNode, "field_ol_summary"));
        bill.setSessionYear(getIntValue(billNode, "field_ol_session"));
        bill.setLastStatus(getValue(billNode, "field_ol_last_status"));
        bill.setLastStatusDate(getDateTimeValue(billNode, "field_ol_last_status_date"));
        bill.setActions(getActionList(billNode, "field_ol_all_actions"));

        if (bill.getBaseBillId().getBillType().isResolution()) {
            // Public Website has different models for resolution and bills. For resolutions action info is stored
            // in the field_ol_all_statuses node.
            bill.setActions(getActionList(billNode, "field_ol_all_statuses"));
        } else {
            bill.setHasSameAs(getBooleanValue(billNode, "field_ol_has_same_as"));
        }

        return bill;
    }

    private List<BillStatusView> getMilestones(JsonNode billNode, String fieldName) {
        TypeReference<ListView<BillStatusView>> billStatusListType = new TypeReference<ListView<BillStatusView>>() {};
        Optional<ListView<BillStatusView>> billStatusList = deserializeValue(billNode, fieldName, billStatusListType);
        return billStatusList.map(ListView::getItems).orElse(ImmutableList.of());
    }

    private List<String> getMembers(JsonNode billNode, String memberFieldName) {
        TypeReference<List<MemberView>> memberListType = new TypeReference<List<MemberView>>() {};
        Optional<List<MemberView>> memberList = deserializeValue(billNode, memberFieldName, memberListType);
        return memberList.orElse(Collections.emptyList()).stream()
                .map(MemberView::getShortName)
                .collect(Collectors.toList());
    }

    private List<BillId> getBillIdList(JsonNode billNode, String fieldName) {
        TypeReference<List<BillIdView>> billIdListType = new TypeReference<List<BillIdView>>() {};
        Optional<List<BillIdView>> billIdViews = deserializeValue(billNode, fieldName, billIdListType);
        return billIdViews.orElse(Collections.emptyList()).stream()
                .map(BillIdView::toBillId)
                .collect(Collectors.toList());
    }

    private List<BillAction> getActionList(JsonNode billNode, String fieldName) {
        TypeReference<ListView<BillActionView>> actionListType = new TypeReference<ListView<BillActionView>>() {};
        Optional<ListView<BillActionView>> actionListView = deserializeValue(billNode, fieldName, actionListType);
        return actionListView
                .map(ListView::getItems)
                .orElse(ImmutableList.of()).stream()
                .map(BillActionView::toBillAction)
                .collect(Collectors.toList());
    }


}
