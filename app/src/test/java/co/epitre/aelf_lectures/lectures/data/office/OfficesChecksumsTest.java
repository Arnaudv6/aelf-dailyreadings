package co.epitre.aelf_lectures.lectures.data.office;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import co.epitre.aelf_lectures.lectures.data.AelfDate;
import co.epitre.aelf_lectures.lectures.data.IsoDate;
import co.epitre.aelf_lectures.lectures.data.OfficeTypes;

class OfficesChecksumsTest {
    @Test
    void nominal() {
        HashMap<String, OfficeChecksum> rawOfficeChecksums = new HashMap<>();
        IsoDate now = new IsoDate();
        rawOfficeChecksums.put("complies", new OfficeChecksum("0326067588ea4e14b3cea8d8139ad910b191d20d6e7477789bf1c76f5e5b1749", now));
        rawOfficeChecksums.put("informations", new OfficeChecksum("e18f20be64ea16794168c5545425f10ebceeff125c206be2c45de2b4038726a4", now));
        rawOfficeChecksums.put("messes", new OfficeChecksum("db8fbc6478911b24fc1369518e0406bee2ddda7bb30a7c5ac43d0cb19715b89e", now));
        rawOfficeChecksums.put("laudes", new OfficeChecksum("7883ffcb441ce6a943703312f8cd51eb157f5617436148bfff95565b56ceda1f", now));

        HashMap<String, Map<String, OfficeChecksum>> rawOfficesChecksums = new HashMap<>();
        rawOfficesChecksums.put("2024-05-14", rawOfficeChecksums);
        OfficesChecksums officesChecksums = new OfficesChecksums(rawOfficesChecksums);

        // Note: Months are 0-indexed
        AelfDate target_date = new AelfDate(2024, 4, 14);

        // Nominal tests
        assertEquals(
                "0326067588ea4e14b3cea8d8139ad910b191d20d6e7477789bf1c76f5e5b1749",
                officesChecksums.getOfficeChecksum(OfficeTypes.COMPLIES, target_date).checksum()
        );
        assertEquals(
                "e18f20be64ea16794168c5545425f10ebceeff125c206be2c45de2b4038726a4",
                officesChecksums.getOfficeChecksum(OfficeTypes.INFORMATIONS, target_date).checksum()
        );
        assertEquals(
                "db8fbc6478911b24fc1369518e0406bee2ddda7bb30a7c5ac43d0cb19715b89e",
                officesChecksums.getOfficeChecksum(OfficeTypes.MESSE, target_date).checksum()
        );
        assertEquals(
                "7883ffcb441ce6a943703312f8cd51eb157f5617436148bfff95565b56ceda1f",
                officesChecksums.getOfficeChecksum(OfficeTypes.LAUDES, target_date).checksum()
        );

        // Validate missing entries
        assertNull(officesChecksums.getOfficeChecksum(OfficeTypes.SEXTE, target_date));
        assertNull(officesChecksums.getOfficeChecksum(OfficeTypes.MESSE, new AelfDate(2024, 4, 15)));
    }
}