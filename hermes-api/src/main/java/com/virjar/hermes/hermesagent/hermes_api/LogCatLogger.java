package com.virjar.hermes.hermesagent.hermes_api;

import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.Marker;

public class LogCatLogger implements Logger {
    private static final String tag = "herems_system";

    @Override
    public String getName() {
        return "Logcat";
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public void trace(String msg) {

    }

    @Override
    public void trace(String format, Object arg) {

    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {

    }

    @Override
    public void trace(String format, Object... arguments) {

    }

    @Override
    public void trace(String msg, Throwable t) {

    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return false;
    }

    @Override
    public void trace(Marker marker, String msg) {

    }

    @Override
    public void trace(Marker marker, String format, Object arg) {

    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {

    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {

    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {

    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public void debug(String msg) {

    }

    @Override
    public void debug(String format, Object arg) {

    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {

    }

    @Override
    public void debug(String format, Object... arguments) {

    }

    @Override
    public void debug(String msg, Throwable t) {

    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return false;
    }

    @Override
    public void debug(Marker marker, String msg) {

    }

    @Override
    public void debug(Marker marker, String format, Object arg) {

    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {

    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {

    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {

    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        Log.i(tag, msg);
    }

    @Override
    public void info(String format, Object arg) {
        Log.i(tag, String.format(format, arg));
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        Log.i(tag, String.format(format, arg1, arg2));
    }

    @Override
    public void info(String format, Object... arguments) {
        Log.i(tag, String.format(format, arguments));
    }

    @Override
    public void info(String msg, Throwable t) {
        Log.i(tag, msg, t);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return false;
    }

    @Override
    public void info(Marker marker, String msg) {

    }

    @Override
    public void info(Marker marker, String format, Object arg) {

    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {

    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {

    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {

    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        Log.w(tag, msg);
    }

    @Override
    public void warn(String format, Object arg) {
        Log.w(tag, String.format(format, arg));
    }

    @Override
    public void warn(String format, Object... arguments) {
        Log.w(tag, String.format(format, arguments));
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        Log.w(tag, String.format(format, arg1, arg2));
    }

    @Override
    public void warn(String msg, Throwable t) {
        Log.w(tag, t);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return false;
    }

    @Override
    public void warn(Marker marker, String msg) {

    }

    @Override
    public void warn(Marker marker, String format, Object arg) {

    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {

    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {

    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {

    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(String msg) {
        Log.e(tag, msg);
    }

    @Override
    public void error(String format, Object arg) {
        Log.e(tag, String.format(format, arg));
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        Log.e(tag, String.format(format, arg1, arg2));
    }

    @Override
    public void error(String format, Object... arguments) {
        Log.e(tag, String.format(format, arguments));
    }

    @Override
    public void error(String msg, Throwable t) {
        Log.e(tag, msg, t);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return false;
    }

    @Override
    public void error(Marker marker, String msg) {

    }

    @Override
    public void error(Marker marker, String format, Object arg) {

    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {

    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {

    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {

    }
}
