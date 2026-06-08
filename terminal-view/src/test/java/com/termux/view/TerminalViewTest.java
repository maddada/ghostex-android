package com.termux.view;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerminalViewTest {

    @Test
    public void shouldFollowOutputAtScrollPositionWhenAtBottom() {
        assertTrue(TerminalView.shouldFollowOutputAtScrollPosition(0));
    }

    @Test
    public void shouldNotFollowOutputAtScrollPositionWhenScrolledOneRowFromBottom() {
        assertFalse(TerminalView.shouldFollowOutputAtScrollPosition(-1));
    }

    @Test
    public void shouldNotFollowOutputAtScrollPositionWhenUserScrolledAwayFromBottom() {
        assertFalse(TerminalView.shouldFollowOutputAtScrollPosition(-4));
    }
}
