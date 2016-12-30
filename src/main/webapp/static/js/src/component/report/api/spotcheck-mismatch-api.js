angular.module('open.spotcheck').factory('SpotcheckMismatchApi', ['$resource', spotcheckMismatchApi]);

function spotcheckMismatchApi($resource) {

    const DATE_FORMAT = 'YYYY-MM-DD h:mm:ss a';
    var mismatchApi = $resource(adminApiPath + "/spotcheck/:datasource/open-mismatches", {datasource: '@datasource'});

    function getMismatches(datasource, contentType, statuses, limit, offset) {
        // TODO: date range
        // TODO: filter API by mismatchStatuses
        // TODO API filter to return only non ignored mismatches?
        var params = {
            datasource: datasource,
            contentType: contentType,
            limit: limit,
            offset: offset
        };
        return mismatchApi.get(params).$promise
            .then(parseMismatches);
    }

    // TODO: Sort mismatches?
    function parseMismatches(response) {
        var result = {
            pagination: {
                total: 0
            },
            mismatches: []
        };
        result.pagination.total = response.total;
        angular.forEach(response.observations, function (observation) {
            angular.forEach(observation.mismatches.items, function (mismatch) {
                if (mismatch.ignoreStatus === 'NOT_IGNORED') {
                    result.mismatches.push(createMismatch(mismatch, observation));
                }
            });
        });
        return result;
    }

    function createMismatch(mismatch, observation) {
        return {
            status: parseStatus(mismatch),
            mismatchType: parseMismatchType(mismatch),
            date: parseDate(observation),
            issue: parseIssues(mismatch),
            refType: parseRefType(observation),
            bill: parseBill(observation),
            calNo: parseCalNo(observation),
            // TODO: Add CalType to API response.
            agendaNo: parseAgendaNo(observation),
            committee: parseCommittee(observation)
        }
    }

    function parseBill(observation) {
        return observation.key.printNo || "";
    }

    function parseDate(observation) {
        return moment(observation.refDateTime).format(DATE_FORMAT);
    }

    function parseRefType(observation) {
        return referenceTypeMap[observation.reportId.referenceType];
    }

    function parseCalNo(observation) {
        return observation.key.calNo || "";
    }

    function parseAgendaNo(observation) {
        if (observation.key.agendaId == null) {
            return "";
        }
        return observation.key.agendaId.number;
    }

    function parseCommittee(observation) {
        if (observation.key.committeeId == null) {
            return "";
        }
        return observation.key.committeeId.name;
    }

    function parseStatus(mismatch) {
        return mismatch.status;
    }

    function parseMismatchType(mismatch) {
        return mismatchMap[mismatch.mismatchType];
    }

    function parseIssues(mismatch) {
        return mismatch.issueIds.items.join(', ')
    }

    return {
        getMismatches: getMismatches
    }
}
