import {
  Button,
  ContextSelector,
  ContextSelectorItem,
  ContextSelectorItemProps,
  Divider,
  Dropdown,
  DropdownItem,
  DropdownToggle,
  Label,
  Spinner,
  Split,
  SplitItem,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import { CheckIcon } from "@patternfly/react-icons";
import { Fragment, useState, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Link, To, useHref } from "react-router-dom";

import { useRealm } from "../../context/realm-context/RealmContext";
import { useRealms } from "../../context/RealmsContext";
import { useRecentRealms } from "../../context/RecentRealms";
import { useWhoAmI } from "../../context/whoami/WhoAmI";
import { toDashboard } from "../../dashboard/routes/Dashboard";
import { toAddRealm } from "../../realm/routes/AddRealm";

import "./realm-selector.css";

type AddRealmProps = {
  onClick: () => void;
};

const AddRealm = ({ onClick }: AddRealmProps) => {
  const { realm } = useRealm();
  const { t } = useTranslation();

  return (
    <Button
      data-testid="add-realm"
      component={(props) => <Link {...props} to={toAddRealm({ realm })} />}
      onClick={onClick}
      isBlock
    >
      {t("createRealm")}
    </Button>
  );
};

type RealmTextProps = {
  name: string;
  displayName: string | undefined;
};

const RealmText = ({ name, displayName }: RealmTextProps) => {
  const { realm } = useRealm();

  return (
    <Split className="keycloak__realm_selector__list-item-split">
      <SplitItem isFilled>
        <Stack>
          <StackItem className="pf-u-font-weight-bold" isFilled>{displayName}</StackItem>
          <StackItem isFilled>{name}</StackItem>
        </Stack>
      </SplitItem>
      <SplitItem>{name === realm && <CheckIcon />}</SplitItem>
    </Split>
  );
};

// We need to make all these props partial because of a bug in PatternFly.
// See: https://github.com/patternfly/patternfly-react/pull/8670
// TODO: Remove this partial when a fix has been released.
type ContextSelectorItemLinkProps = Partial<
  Omit<ContextSelectorItemProps, "href">
> & {
  to: To;
};

const ContextSelectorItemLink = ({
  to,
  ...props
}: ContextSelectorItemLinkProps) => {
  const href = useHref(to);
  return <ContextSelectorItem {...props} href={href} />;
};

export const RealmSelector = () => {
  const { realm } = useRealm();
  const { realms, refresh } = useRealms();
  const [realmDisplayName, setRealmDisplayName] = useState("");
  const { whoAmI } = useWhoAmI();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const { t } = useTranslation();
  const recentRealms = useRecentRealms();

  const all = useMemo(() => {
    const displayName = realms.find((r) => r.name === realm)?.displayName;
    setRealmDisplayName(displayName ? displayName : "");
    return realms
      .filter((r) => r.name !== realm)
      .map((realm) => {
        const used = recentRealms.some((name) => name === realm.name);
        return { realm, used };
      })
      .sort((r1, r2) => {
        if (r1.used == r2.used) return 0;
        if (r1.used) return -1;
        if (r2.used) return 1;
        return 0;
      });
  }, [recentRealms, realm, realms]);

  const filteredItems = useMemo(
    () =>
      search.trim() === ""
        ? all
        : all.filter(
            (r) =>
              r.realm.name.toLowerCase().includes(search.toLowerCase()) ||
              r.realm.displayName?.toLowerCase().includes(search.toLowerCase()),
          ),
    [search, all],
  );

  if (realms.length === 0) {
    refresh();
  }

  return realms.length > 5 ? (
    <ContextSelector
      data-testid="realmSelector"
      toggleText={realmDisplayName ? realmDisplayName : realm}
      isOpen={open}
      screenReaderLabel={realmDisplayName ? realmDisplayName : realm}
      onToggle={() => setOpen(!open)}
      searchInputValue={search}
      onSearchInputChange={(value) => setSearch(value)}
      className="keycloak__realm_selector__context_selector"
      footer={
        whoAmI.canCreateRealm() && (
          <ContextSelectorItem key="add">
            <AddRealm onClick={() => setOpen(false)} />
          </ContextSelectorItem>
        )
      }
    >
      {filteredItems.map((item) => (
        <ContextSelectorItemLink
          key={item.realm.name}
          to={toDashboard({ realm: item.realm.name })}
          onClick={() => setOpen(false)}
        >
          <RealmText {...item.realm} />{" "}
          {item.used && <Label>{t("recent")}</Label>}
        </ContextSelectorItemLink>
      ))}
    </ContextSelector>
  ) : (
    <Dropdown
      id="realm-select"
      data-testid="realmSelector"
      className="keycloak__realm_selector__dropdown"
      isOpen={open}
      toggle={
        <DropdownToggle
          data-testid="realmSelectorToggle"
          onToggle={() => {
            setOpen(!open);
          }}
          className="keycloak__realm_selector_dropdown__toggle"
        >
          {realmDisplayName ? realmDisplayName : realm}
        </DropdownToggle>
      }
      dropdownItems={(realms.length !== 0
        ? realms.map((realm) => (
            <DropdownItem
              key={realm.name}
              component={
                <Link
                  to={toDashboard({ realm: realm.name })}
                  onClick={() => setOpen(false)}
                >
                  <RealmText {...realm} />
                </Link>
              }
            />
          ))
        : [
            <DropdownItem key="loader">
              <Spinner size="sm" /> {t("loadingRealms")}
            </DropdownItem>,
          ]
      ).concat([
        <Fragment key="add-realm">
          {whoAmI.canCreateRealm() && (
            <>
              <Divider key="divider" />
              <DropdownItem key="add" component="div">
                <AddRealm onClick={() => setOpen(false)} />
              </DropdownItem>
            </>
          )}
        </Fragment>,
      ])}
    />
  );
};
