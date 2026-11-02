package edu.usc.splitsmart.domain.contract;

/** FRESH never silently falls back to cache. CACHE_ONLY never starts network I/O. */
public enum ReadPolicy { FRESH, CACHE_ONLY }
