package com.newterraearth.tfe.common.entity;

import java.util.List;

import net.dries007.tfc.util.calendar.Month;

public interface NTEFaunaAccess
{
    List<Month> tfe$getMonths();

    /**
     * Minimum connected water volume (in water blocks) required at the spawn position. {@code <= 0} disables the check.
     */
    int tfe$getMinWaterVolume();
}
