package com.theitdojo.optimizing_llm_responses_with_rag_in_java.views;

import com.theitdojo.optimizing_llm_responses_with_rag_in_java.state.RagState;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;

/**
 * The main view is a top-level placeholder for other views.
 */
@Layout
@AnonymousAllowed
public class MainLayout extends AppLayout {

    private H1 viewTitle;
    private final RagState ragState;

    public MainLayout(RagState ragState) {
        this.ragState = ragState;
        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menu toggle");
        toggle.addClassNames(LumoUtility.Margin.NONE);

        viewTitle = new H1();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);


        var container = new Div();
        container.addClassNames(LumoUtility.Display.FLEX, LumoUtility.JustifyContent.BETWEEN, LumoUtility.Width.FULL, LumoUtility.AlignItems.CENTER);

        Checkbox ragToggle = new Checkbox("Activar RAG", ragState.isEnabled());
        ragToggle.addValueChangeListener(e -> ragState.setEnabled(e.getValue()));
        ragToggle.addClassNames(LumoUtility.Margin.Right.MEDIUM);

        container.add(viewTitle, ragToggle);
        addToNavbar(true, toggle, container);
    }

    private void addDrawerContent() {
        Scroller scroller = new Scroller(createNavigation());
        addToDrawer(scroller, createFooter());
    }

    private SideNav createNavigation() {
        SideNav nav = new SideNav();

        List<MenuEntry> menuEntries = MenuConfiguration.getMenuEntries();
        menuEntries.forEach(entry -> {
            if (entry.icon() != null) {
                nav.addItem(new SideNavItem(entry.title(), entry.path(), new SvgIcon(entry.icon())));
            } else {
                nav.addItem(new SideNavItem(entry.title(), entry.path()));
            }
        });

        return nav;
    }

    private Footer createFooter() {
        return new Footer();
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        return MenuConfiguration.getPageHeader(getContent()).orElse("");
    }
}
