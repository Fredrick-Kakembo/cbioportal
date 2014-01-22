/** Copyright (c) 2012 Memorial Sloan-Kettering Cancer Center.
**
** This library is free software; you can redistribute it and/or modify it
** under the terms of the GNU Lesser General Public License as published
** by the Free Software Foundation; either version 2.1 of the License, or
** any later version.
**
** This library is distributed in the hope that it will be useful, but
** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
** documentation provided hereunder is on an "as is" basis, and
** Memorial Sloan-Kettering Cancer Center 
** has no obligations to provide maintenance, support,
** updates, enhancements or modifications.  In no event shall
** Memorial Sloan-Kettering Cancer Center
** be liable to any party for direct, indirect, special,
** incidental or consequential damages, including lost profits, arising
** out of the use of this software and its documentation, even if
** Memorial Sloan-Kettering Cancer Center 
** has been advised of the possibility of such damage.  See
** the GNU Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public License
** along with this library; if not, write to the Free Software Foundation,
** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
**/

package org.mskcc.cbio.portal.dao;

import org.mskcc.cbio.portal.model.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAO to `patient`.
 * 
 * @author Benjamin Gross
 */
public class DaoPatient {

    private static final Map<String, Patient> byStableId = new ConcurrentHashMap<String, Patient>();
    private static final Map<Integer, Patient> byInternalId = new ConcurrentHashMap<Integer, Patient>();
    private static final Map<String, List<Patient>> byStableCancerStudyId = new ConcurrentHashMap<String, List<Patient>>();

    static {
        cache();
    }

    private static void clearCache()
    {
        byStableId.clear();
        byInternalId.clear();
        byStableCancerStudyId.clear();
    }

    private static void cache()
    {
        clearCache();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = JdbcUtil.getDbConnection(DaoPatient.class);
            pstmt = con.prepareStatement("SELECT * FROM patient");
            rs = pstmt.executeQuery();
            ArrayList<Patient> list = new ArrayList<Patient>();
            while (rs.next()) {
                cachePatient(extractPatient(rs));
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        finally {
            JdbcUtil.closeAll(DaoPatient.class, con, pstmt, rs);
        }
    }

    private static void cachePatient(Patient patient)
    {
        byStableId.put(patient.getStableId(), patient);
        byInternalId.put(patient.getInternalId(), patient);
        if (byStableCancerStudyId.containsKey(patient.getCancerStudy().getCancerStudyStableId())) {
            byStableCancerStudyId.get(patient.getCancerStudy().getCancerStudyStableId()).add(patient);
        }
        else {
            List<Patient> patientList = new ArrayList<Patient>();
            patientList.add(patient);
            byStableCancerStudyId.put(patient.getCancerStudy().getCancerStudyStableId(), patientList);
        }
    }

    public static int addPatient(Patient patient) throws DaoException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = JdbcUtil.getDbConnection(DaoPatient.class);
            pstmt = con.prepareStatement("INSERT INTO patient (`STABLE_ID`, `CANCER_STUDY_ID`) VALUES (?,?)",
                                         Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, patient.getStableId());
            pstmt.setInt(2, patient.getCancerStudy().getInternalId());
            pstmt.executeUpdate();
            rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                cachePatient(new Patient(patient.getCancerStudy(), rs.getInt(1), patient.getStableId()));
                return rs.getInt(1);
            }
            return -1;
        }
        catch (SQLException e) {
            throw new DaoException(e);
        }
        finally {
            JdbcUtil.closeAll(DaoPatient.class, con, pstmt, rs);
        }
    }

    public static Patient getPatientByInternalId(int internalId)
    {
        return byInternalId.get(internalId);
    }

    public static Patient getPatientByStableId(String stableId)
    {
        return byStableId.get(stableId);
    }

    public static List<Patient> getPatientsByStableCancerStudyId(String cancerStudyId)
    {
        return byStableCancerStudyId.get(cancerStudyId);
    }

    public static List<Patient> getAllPatients()
    {
        return new ArrayList<Patient>(byStableId.values());
    }

    public static void deleteAllRecords() throws DaoException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = JdbcUtil.getDbConnection(DaoPatient.class);
            pstmt = con.prepareStatement("TRUNCATE TABLE patient");
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            throw new DaoException(e);
        }
        finally {
            JdbcUtil.closeAll(DaoPatient.class, con, pstmt, rs);
        }

        clearCache();
    }

    private static Patient extractPatient(ResultSet rs) throws SQLException
    {
        CancerStudy cancerStudy = DaoCancerStudy.getCancerStudyByInternalId(rs.getInt("CANCER_STUDY_ID"));
        return new Patient(cancerStudy,
                           rs.getInt("INTERNAL_ID"),
                           rs.getString("STABLE_ID"));
    }
}
