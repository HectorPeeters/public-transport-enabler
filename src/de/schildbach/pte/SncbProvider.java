/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.pte;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyLocationsResult;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.QueryTripsContext;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.ResultHeader;
import de.schildbach.pte.dto.StationDepartures;
import de.schildbach.pte.dto.SuggestLocationsResult;
import de.schildbach.pte.dto.TripOptions;
import de.schildbach.pte.exception.InternalErrorException;
import de.schildbach.pte.exception.NotFoundException;
import okhttp3.HttpUrl;

/**
 * Provider implementation for the Nationale Maatschappij der Belgische
 * Spoorwegen (Belgium).
 * 
 * @author Hector Peeters
 */

public class SncbProvider extends AbstractNetworkProvider {

    private static String SERVER_PRODUCT = "irail";

    public enum Language {
        Nl,
        Fr,
        En,
        De,
    }

    private Language language;
    private ResultHeader resultHeader;

    public SncbProvider() {
        this(Language.En);
    }

    public SncbProvider(Language language) {
        super(NetworkId.SNCB);
        this.language = language;
        this.resultHeader = new ResultHeader(network, SERVER_PRODUCT);
    }

    @Override
    public NearbyLocationsResult queryNearbyLocations(Set<LocationType> types, Location location, int maxDistance,
            int maxLocations) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'queryNearbyLocations'");
    }

    @Override
    public QueryDeparturesResult queryDepartures(String stationId, Date time, int maxDepartures, boolean equivs)
            throws IOException {
        HttpUrl url = HttpUrl.parse("https://api.irail.be/")
                .newBuilder()
                .addPathSegment("liveboard")
                .addQueryParameter("format", "json")
                .addQueryParameter("lang", "en")
                .addQueryParameter("station", stationId).build();

        final CharSequence page;
        try {
            page = httpClient.get(url);
        } catch (InternalErrorException | NotFoundException e) {
            return new QueryDeparturesResult(this.resultHeader, QueryDeparturesResult.Status.INVALID_STATION);
        } catch (Exception e) {
            System.out.println(e);
            return new QueryDeparturesResult(this.resultHeader, QueryDeparturesResult.Status.SERVICE_DOWN);
        }

        QueryDeparturesResult queryDeparturesResult = new QueryDeparturesResult(this.resultHeader);

        try {
            JSONObject head = new JSONObject(page.toString());

            String version = head.getString("version");
            assert version.equals("1.2");

            JSONArray departures = head.getJSONObject("departures").getJSONArray("departure");

            System.out.println(departures);

            Map<Location, List<Departure>> departuresResult = new HashMap<>();
            Map<Location, List<LineDestination>> lineDestinationResult = new HashMap<>();

            for (int i = 0; i < departures.length(); i++) {
                JSONObject departure = departures.getJSONObject(i);
                JSONObject stationInfo = departure.getJSONObject("stationinfo");

                JSONObject vehicleInfo = departure.getJSONObject("vehicleinfo");
                String operator = vehicleInfo.getString("name").split("\\.")[1];

                int plannedTimeUnix = Integer.parseInt(departure.getString("time"));
                Date plannedTime = new Date((long) plannedTimeUnix * 1000);
                int predictedTimeUnix = plannedTimeUnix + Integer.parseInt(departure.getString("delay"));
                Date predictedTime = new Date((long) predictedTimeUnix * 1000);
                Line line = new Line(null, operator, null, null, null, null, null, null);
                Position position = null;
                String station = departure.getString("station");

                Location destination = new Location(LocationType.STATION, station,
                        Point.fromDouble(stationInfo.getDouble("locationX"), stationInfo.getDouble("locationY")),
                        null,
                        departure.getString("station"));
                int[] capacity = null;
                String message = null;

                if (!departuresResult.containsKey(destination))
                    departuresResult.put(destination, new ArrayList<>());
                departuresResult.get(destination).add(
                        new Departure(plannedTime, predictedTime, line, position, destination, capacity, message));

                if (!lineDestinationResult.containsKey(destination))
                    lineDestinationResult.put(destination, new ArrayList<>());

                lineDestinationResult.get(destination).add(new LineDestination(line, destination));
            }

            for (Map.Entry<Location, List<Departure>> entry : departuresResult.entrySet()) {
                queryDeparturesResult.stationDepartures.add(new StationDepartures(entry.getKey(), entry.getValue(),
                        lineDestinationResult.get(entry.getKey())));
            }

        } catch (final JSONException e) {
            throw new RuntimeException("Cannot parse: '" + page + "' on " + url, e);
        }

        return queryDeparturesResult;
    }

    @Override
    public SuggestLocationsResult suggestLocations(CharSequence constraint, Set<LocationType> types, int maxLocations)
            throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'suggestLocations'");
    }

    @Override
    public QueryTripsResult queryTrips(Location from, Location via, Location to, Date date, boolean dep,
            TripOptions options) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'queryTrips'");
    }

    @Override
    public QueryTripsResult queryMoreTrips(QueryTripsContext context, boolean later) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'queryMoreTrips'");
    }

    @Override
    protected boolean hasCapability(Capability capability) {
        switch (capability) {
            case DEPARTURES:
                return true;
            default:
                return false;
        }
    }
}
